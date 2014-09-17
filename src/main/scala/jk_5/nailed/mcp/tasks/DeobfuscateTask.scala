package jk_5.nailed.mcp.tasks

import java.io.{BufferedOutputStream, File, FileOutputStream}
import java.util.zip.{ZipEntry, ZipFile, ZipOutputStream}

import com.google.common.base.Charsets
import com.google.common.collect.Lists
import com.google.common.io.{ByteStreams, Files, LineProcessor}
import de.oceanlabs.mcp.mcinjector.MCInjectorImpl
import jk_5.nailed.mcp.delayed.DelayedFile
import jk_5.nailed.mcp.json.JsonFactory
import jk_5.nailed.mcp.tasks.CachedTask.Cached
import jk_5.nailed.mcp.{Constants, NailedMCPExtension}
import net.md_5.specialsource._
import net.md_5.specialsource.provider.{JarProvider, JointProvider}

import scala.collection.convert.wrapAsScala._
import scala.collection.mutable
import org.gradle.api.tasks._
import org.objectweb.asm.Opcodes.{ACC_FINAL, ACC_PRIVATE, ACC_PROTECTED, ACC_PUBLIC}
import org.objectweb.asm.tree.{ClassNode, FieldNode, MethodNode}
import org.objectweb.asm.{ClassReader, ClassWriter, Opcodes}

/**
 * No description given
 *
 * @author jk-5
 */
class DeobfuscateTask extends CachedTask {

  @InputFile @Optional private var fieldCsv: DelayedFile = _
  @InputFile @Optional private var methodCsv: DelayedFile = _

  @InputFile private var inJar: DelayedFile = _
  @InputFile private var srg: DelayedFile = _
  @InputFile private var exceptorCfg: DelayedFile = _
  @InputFile private var exceptorJson: DelayedFile = _

  @Input @Optional private var stripSynthetics = false
  @Input private var applyMarkers = false

  @InputFiles private val accessTransformers = Lists.newArrayList[DelayedFile]()

  @OutputFile @Cached private var outJar: DelayedFile = _
  private var log: DelayedFile = _

  @TaskAction def doTask(){
    val tempObfJar = new File(getTemporaryDir, "postDeobf.jar")
    val tempExcJar = if(stripSynthetics) new File(getTemporaryDir, "postExceptor.jar") else getOutJar
    val ats = mutable.HashSet[File]()
    this.accessTransformers.foreach(t => ats.add(getProject.file(t).getCanonicalFile))

    getLogger.lifecycle("Applying SpecialSource", new Array[String](0))
    deobfuscateJar(getInJar, tempObfJar, getSrg, ats)

    val log = if(getLog != null) getLog else new File(getTemporaryDir, "exceptor.log")

    getLogger.lifecycle("Applying Exceptor", new Array[String](0))
    applyExceptor(tempObfJar, tempExcJar, getExceptorConfig, log, ats)

    if(stripSynthetics){
      getLogger.lifecycle("Stripping Synthetics", new Array[String](0))
      stripSynthetics(tempExcJar, getOutJar)
    }
  }

  def deobfuscateJar(inJar: File, outJar: File, srg: File, ats: mutable.HashSet[File]){
    val mapping = new JarMapping
    mapping.loadMappings(srg)

    val renames = mutable.HashMap[String, String]()
    Array(getFieldCsv, getMethodCsv).filter(_ != null).foreach{ f =>
      Files.readLines(f, Charsets.UTF_8, new LineProcessor[String]{
        override def getResult = null

        override def processLine(line: String) = {
          val pts = line.split(",")
          if(pts(0) != "searge"){
            renames.put(pts(0), pts(1))
          }
          true
        }
      })
    }

    val accessMap = new AccessMap(){
      override def addAccessChange(symbolString: String, accessString: String){
        val pts = symbolString.split(" ")
        if(pts.length >= 2){
          val idx = pts(1).indexOf('(')
          var start = pts(1)
          var end = ""
          if(idx != -1){
            start = pts(1).substring(0, idx)
            end = pts(1).substring(idx)
          }
          renames.get("start") match {
            case Some(r) => pts(1) = r + end
            case None =>
          }
        }
        super.addAccessChange(pts.mkString("."), accessString)
      }
    }

    getLogger.lifecycle("Applying AccessTransformers", new Array[AnyRef](0): _*)
    for(at <- ats){
      getLogger.info("  {}", Array(at): _*)
      accessMap.loadAccessTransformer(at)
    }

    val srgProcessor = new RemapperProcessor(null, mapping, null)
    val accessProcessor = new RemapperProcessor(null, null, accessMap)
    val remapper = new JarRemapper(srgProcessor, mapping, accessProcessor)
    val input = Jar.init(inJar)
    val inheritanceProviders = new JointProvider
    inheritanceProviders.add(new JarProvider(input))
    mapping.setFallbackInheritanceProvider(inheritanceProviders)

    remapper.remapJar(input, outJar)
  }

  def applyExceptor(inJar: File, outJar: File, config: File, log: File, accessTransformers: mutable.HashSet[File]){
    val excJson = getExceptorJson
    var json: String = null
    if(excJson != null){
      val entries = JsonFactory.loadExcepterJson(excJson)
      for(at <- accessTransformers){
        getLogger.info("Fixing exceptor config for AccessTransformer {}", at.getCanonicalPath)
        Files.readLines(at, Charsets.UTF_8, new LineProcessor[Any]{
          override def getResult = null
          override def processLine(l: String): Boolean = {
            var line = l
            if(line.indexOf('#') != -1) line = line.substring(0, line.indexOf('#'))
            line = line.trim.replace('.', '/')
            if(line.isEmpty) return true

            val s = line.split(" ")

            if(s.length == 2 && s(1).indexOf('$') > 0){
              val parent = s(1).substring(0, s(1).indexOf('$'))
              for(e <- Array(entries.get(parent), entries.get(s(1))).filter(c => c != null && c.innerClasses != null)){
                for(inner <- e.innerClasses.filter(_.inner_class == s(1))){
                  val access = fixAccess(inner.getAccess, s(0))
                  inner.access = if(access == 0) null else Integer.toHexString(access)
                }
              }
            }
            true
          }
        })
      }
      val jsonTmp = new File(this.getTemporaryDir, "transformed.json")
      json = jsonTmp.getCanonicalPath
      Files.write(JsonFactory.gson.toJson(entries).getBytes, jsonTmp)
    }
    val ext = getProject.getExtensions.getByName(Constants.MCP_EXTENSION_NAME).asInstanceOf[NailedMCPExtension]
    val genParams = ext.getMinecraftVersion != "1.7.2" //We want this after minecraft 1.7.2, so just check it here

    getLogger.debug("INPUT: {}", inJar)
    getLogger.debug("OUTPUT: {}", outJar)
    getLogger.debug("CONFIG: {}", config)
    getLogger.debug("JSON: {}", json)
    getLogger.debug("LOG: {}", log)
    getLogger.debug("PARAMS: {}", genParams)

    MCInjectorImpl.process(inJar.getCanonicalPath, outJar.getCanonicalPath, config.getCanonicalPath, log.getCanonicalPath, null, 0, json, isApplyMarkers, genParams)
  }

  def fixAccess(access: Int, target: String): Int = {
    var ret = access & ~7
    val t = if(target.startsWith("public")) ACC_PUBLIC
      else if(target.startsWith("protected")) ACC_PROTECTED
      else if(target.startsWith("private")) ACC_PRIVATE
      else 0
    access & 7 match {
      case ACC_PRIVATE => ret |= t
      case 0 => ret |= (if(t != ACC_PRIVATE) t else 0)
      case ACC_PROTECTED => ret |= (if(t != ACC_PRIVATE && t != 0) t else ACC_PROTECTED)
      case ACC_PUBLIC => ret |= ACC_PUBLIC
    }
    if(target.endsWith("-f")) ret &= ~ACC_FINAL
    else if(target.endsWith("+f")) ret |= ACC_FINAL
    ret
  }

  def stripSynthetics(inJar: File, outJar: File){
    val in = new ZipFile(inJar)
    val out = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(outJar)))

    for(entry <- in.entries().filter(e => !e.getName.contains("META-INF"))){
      if(entry.isDirectory){
        out.putNextEntry(entry)
      }else{
        val n = new ZipEntry(entry.getName)
        n.setTime(entry.getTime)
        out.putNextEntry(n)
        var data = ByteStreams.toByteArray(in.getInputStream(entry))
        if(entry.getName.endsWith(".class")){
          data = stripSynthetics(entry.getName, data)
        }
        out.write(data)
      }
    }
  }

  def stripSynthetics(name: String, data: Array[Byte]): Array[Byte] = {
    val reader = new ClassReader(data)
    val cnode = new ClassNode()
    reader.accept(cnode, 0)
    if((cnode.access & Opcodes.ACC_ENUM) == 0 && !cnode.superName.equals("java/lang/Enum") && (cnode.access & Opcodes.ACC_SYNTHETIC) == 0){
      for(f <- cnode.fields.asInstanceOf[java.util.List[FieldNode]]){
        f.access = f.access & (0xffffffff-Opcodes.ACC_SYNTHETIC)
      }
      for(f <- cnode.methods.asInstanceOf[java.util.List[MethodNode]]){
        f.access = f.access & (0xffffffff-Opcodes.ACC_SYNTHETIC)
      }
    }
    val writer = new ClassWriter(0)
    cnode.accept(writer)
    writer.toByteArray
  }

  def addAccessTransformer(obj: Any*) = obj.foreach{
    case f: DelayedFile => this.accessTransformers.add(f)
    case f: File => this.accessTransformers.add(new DelayedFile(f.getAbsolutePath, getProject))
    case s: String => this.accessTransformers.add(new DelayedFile(s, getProject))
    case o => this.accessTransformers.add(new DelayedFile(o.toString, getProject))
  }

  def setExceptorConfig(exceptorConfig: DelayedFile) = this.exceptorCfg = exceptorConfig
  def setExceptorJson(exceptorJson: DelayedFile) = this.exceptorJson = exceptorJson
  def setApplyMarkers(applyMarkers: Boolean) = this.applyMarkers = applyMarkers
  def setInJar(inJar: DelayedFile) = this.inJar = inJar
  def setLog(log: DelayedFile) = this.log = log
  def setSrg(srg: DelayedFile) = this.srg = srg
  def setOutJar(outJar: DelayedFile) = this.outJar = outJar
  def setFieldCsv(fieldCsv: DelayedFile) = this.fieldCsv = fieldCsv
  def setMethodCsv(methodCsv: DelayedFile) = this.methodCsv = methodCsv
  def setStripSynthetics(stripSynthetics: Boolean) = this.stripSynthetics = stripSynthetics

  def getExceptorConfig = this.exceptorCfg.call()
  def getExceptorJson = if(this.exceptorJson != null) this.exceptorJson.call() else null
  def isApplyMarkers = this.applyMarkers
  def getInJar = this.inJar.call()
  def getLog = if(this.log == null) null else this.log.call()
  def getSrg = this.srg.call()
  def getOutJar = this.outJar.call()
  def getAccessTransformers = getProject.files(this.accessTransformers)
  def getFieldCsv = this.fieldCsv.call()
  def getMethodCsv = this.methodCsv.call()
  def getStripSynthetics = this.stripSynthetics
}
