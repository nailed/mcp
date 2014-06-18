package jk_5.nailed.mcp.tasks

import _root_.scala.Some
import scala.collection.mutable
import scala.collection.convert.wrapAsScala._
import jk_5.nailed.mcp.tasks.common.CachedTask
import org.gradle.api.tasks._
import jk_5.nailed.mcp.delayed.DelayedFile
import jk_5.nailed.mcp.tasks.common.CachedTask.Cached
import com.google.common.collect.Lists
import java.io.File
import net.md_5.specialsource._
import com.google.common.io.{LineProcessor, Files}
import com.google.common.base.{Joiner, Charsets}
import net.md_5.specialsource.provider.{JarProvider, JointProvider}

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
    val tempExcJar = if(stripSynthetics) new File(getTemporaryDir, "postExceptor.jar")
    val ats = mutable.HashSet[File]()
    this.accessTransformers.foreach(t => ats.add(getProject.file(t).getCanonicalFile))

    getLogger.lifecycle("Applying SpecialSource", new Array[String](0))
    deobfuscateJar(getInJar, tempObfJar, getSrg, ats)

    /*val log = if(getLog == null) getLog else new File(getTemporaryDir, "exceptor.log")

    getLogger.lifecycle("Applying Exceptor", new Array[String](0))
    applyExceptor(tempObfJar, tempExcJar, getExceptorConfig, log, ats)

    if(stripSynthetics){
      getLogger.lifecycle("Stripping Synthetics", new Array[String](0))
      stripSynthetics(tempExcJar, getOutJar)
    }*/
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
          if(idx != 1){
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

    getLogger.info("Applying AccessTransformers", new Array[AnyRef](0): _*)
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
  def getExceptorJson = this.exceptorJson.call()
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
