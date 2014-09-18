package jk_5.nailed.mcp.tasks

import java.io._
import java.util.zip.ZipInputStream

import com.google.code.regexp.{Matcher, Pattern}
import com.google.common.base.{Charsets, Throwables}
import com.google.common.io.{ByteStreams, Files}
import jk_5.nailed.mcp.delayed.DelayedFile
import jk_5.nailed.mcp.io.{CachedInputSupplier, SequencedInputSupplier}
import jk_5.nailed.mcp.{Constants, HashUtils}
import net.minecraftforge.srg2source.ast.RangeExtractor
import net.minecraftforge.srg2source.util.io.{FolderSupplier, InputSupplier, ZipInputSupplier}
import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks._
import org.objectweb.asm.{ClassReader, ClassVisitor, MethodVisitor, Opcodes}

import _root_.scala.collection.JavaConverters._
import _root_.scala.collection.convert.wrapAsScala._
import _root_.scala.collection.mutable

/**
 * No description given
 *
 * @author jk-5
 */
class ExtractRangeMapTask extends DefaultTask {

  @InputFiles private var libs: FileCollection = _
  @OutputFile private var rangeMap: DelayedFile = _
  private val inputs = mutable.ArrayBuffer[DelayedFile]()
  private val configurations = mutable.ArrayBuffer[Configuration]()
  private var allCached = false
  @Optional @OutputFile private var staticsList: DelayedFile = _
  @Optional @InputFile private var cleanCompiled: DelayedFile = _

  private final val FILE_FROM = Pattern.compile("\\s+@\\|([\\w\\d/.]+)\\|.*$")
  private final val FILE_START = Pattern.compile("\\s*Class Start\\: ([\\w\\d.]+)$")

  @TaskAction def doTask(){
    val inputs = getInputFiles
    val rangemap = this.getRangeMap

    if(getCleanCompiled != null){
      extractStaticInfo(getCleanCompiled, getStaticsList)
    }

    if(inputs.size == 0) return
    val supplier = cache(if(inputs.size == 1) getInputSupplier(inputs(0)) else {
      val s = new SequencedInputSupplier
      inputs.foreach(i => s.add(getInputSupplier(i)))
      s
    }, rangemap)

    if(rangemap.exists()){
      if(allCached) return
      val files = supplier.gatherAll(".java")
      val lines = Files.readLines(rangemap, Charsets.UTF_8)
      val it = lines.iterator()
      while(it.hasNext){
        val line = it.next()
        var m: Matcher = null
        var fileMatch: String = null
        if(line.trim.startsWith("@")){
          m = FILE_FROM.matcher(line)
          if(m.matches()) fileMatch = m.group(1).replace('\\', '/')
        }else{
          m = FILE_START.matcher(line)
          if(m.matches) fileMatch = m.group(1).replace('.', '/') + ".java"
        }
        if(fileMatch != null && files.contains(fileMatch)) it.remove()
      }
      generateRangeMap(supplier, rangemap)

      lines.addAll(Files.readLines(rangemap, Charsets.UTF_8))
      Files.write(lines.mkString(Constants.NEWLINE), rangemap, Charsets.UTF_8)
    }else generateRangeMap(supplier, rangemap)
  }

  def cache(supplier: InputSupplier, rangemap: File): InputSupplier = {
    val mapExists = rangemap.exists()
    val cacheFile = new File(rangemap.getAbsolutePath + ".inputCache")
    val cache = readCache(cacheFile)
    val strings = supplier.gatherAll(".java")
    val genCache = mutable.HashSet[CacheEntry]()
    val cachedSuppler = new CachedInputSupplier
    for(s <- strings){
      val root = new File(supplier.getRoot(s)).getCanonicalFile
      val is = supplier.getInput(s)
      val array = ByteStreams.toByteArray(is)
      is.close()
      val entry = new CacheEntry(s, root, HashUtils.hash(array))
      genCache.add(entry)
      if(!mapExists || !cache.contains(entry)) cachedSuppler.addFile(s, root, array)
    }
    if(!cachedSuppler.isEmpty) writeCache(cacheFile, genCache)
    else allCached = true
    cachedSuppler
  }

  def readCache(cacheFile: File): mutable.HashSet[CacheEntry] = {
    if(!cacheFile.exists()) return mutable.HashSet[CacheEntry]()

    val lines = Files.readLines(cacheFile, Charsets.UTF_8)
    val cache = mutable.HashSet[CacheEntry]()

    for(s <- lines){
      val tokens = s.split(";")
      if(tokens.length != 3){
        getLogger.info("Corrupted input cache! {}", cacheFile)
      }else{
        cache.add(new CacheEntry(tokens(0), new File(tokens(1)), tokens(2)))
      }
    }

    cache
  }

  def writeCache(file: File, cache: mutable.HashSet[CacheEntry]){
    if(file.exists()) file.delete()
    file.getParentFile.mkdirs()
    file.createNewFile()
    val writer = Files.newWriter(file, Charsets.UTF_8)
    for(e <- cache){
      writer.write(e.toString())
      writer.newLine()
    }
    writer.close()
  }

  def generateRangeMap(input: InputSupplier, rangeMap: File){
    val extractor = new RangeExtractor
    extractor.addLibs(getLibs.getAsPath).setSrc(input)
    //TODO: proper logging
    //val stream = new PrintStream(Constants.createLogger(getLogger(), LogLevel.DEBUG))
    //extractor.setOutLogger(stream);
    val worked = extractor.generateRangeMap(rangeMap)
    if(!worked) throw new RuntimeException("RangeMap generation Failed!")
  }

  def getInputSupplier(file: File): InputSupplier =
    if(file.isDirectory) new FolderSupplier(file)
    else if(file.getName.endsWith(".zip") || file.getName.endsWith(".jar")){
      val s = new ZipInputSupplier
      s.readZip(file)
      s
    }else throw new IllegalArgumentException("Can only make RangeExtractor input supplier from a directory or a zip/jar")

  def getInputFiles: mutable.ArrayBuffer[File] = this.inputs.map(_.call())

  @InputFiles def getCacheInputs = getProject.files(this.inputs.asJava)

  def setRangeMap(rangeMap: DelayedFile) = this.rangeMap = rangeMap
  def getRangeMap = this.rangeMap.call()
  def addConfiguration(configuration: Configuration) = this.configurations += configuration
  def addInput(input: DelayedFile) = this.inputs += input
  def setStaticsList(excOut: DelayedFile) = this.staticsList = excOut
  def getStaticsList = this.staticsList.call()
  def setCleanCompiled(cleanCompiled: DelayedFile) = this.cleanCompiled = cleanCompiled
  def getCleanCompiled = if(this.cleanCompiled == null) null else this.cleanCompiled.call()

  def getLibs: FileCollection = {
    if(libs == null){
      //FIXME
      /*for(config <- configurations){
        libs = getProject.files(config, libs.asJava)
      }*/
      libs = getProject.files(getProject.getConfigurations.getByName("compile"))
    }
    libs
  }

  private def extractStaticInfo(compiled: File, output: File){
    try{
      if(output.exists()){
        output.delete()
      }

      output.getParentFile.mkdirs()
      output.createNewFile()

      val writer = Files.newWriter(output, Charsets.UTF_8)
      var inJar: ZipInputStream = null
      try{
        inJar = new ZipInputStream(new BufferedInputStream(new FileInputStream(compiled)))

        var stop = false
        while(!stop){
          val entry = inJar.getNextEntry
          if(entry == null) stop = true
          if(!stop && !entry.isDirectory && entry.getName.endsWith(".class") && entry.getName.startsWith("net/minecraft/")){
            getLogger.debug("Processing {}", entry.getName)
            val data = new Array[Byte](4096)
            val entryBuffer = new ByteArrayOutputStream()
            var len: Int = -1
            do{
              len = inJar.read(data)
              if(len > 0) entryBuffer.write(data, 0, len)
            }while(len != -1)
            val entryData = entryBuffer.toByteArray
            new ClassReader(entryData).accept(new GenerateMapAdapter(writer), 0)
          }
        }
      }finally{
        if(inJar != null){
          inJar.close()
        }
      }
      writer.close();
    }catch{
      case e: IOException => Throwables.propagate(e)
    }
  }

  class CacheEntry(p: String, r: File, val hash: String) {
    val path = p.replace('\\', '/')
    val root = r.getCanonicalFile

    override def hashCode(): Int = {
      val prime = 31
      var result = 1
      result = prime * result + (if(hash == null) 0 else hash.hashCode())
      result = prime * result + (if(path == null) 0 else path.hashCode())
      result = prime * result + (if(root == null) 0 else root.hashCode())
      result
    }

    override def equals(obj: Any): Boolean = {
      if(obj == null) return false
      if(getClass != obj.getClass) return false
      val other = obj.asInstanceOf[CacheEntry]
      if(hash == null){
        if(other.hash != null) return false
      }else if(!hash.equals(other.hash)) return false
      if(path == null){
        if(other.path != null) return false
      }else if(!path.equals(other.path)) return false
      if(root == null){
        if(other.root != null) return false
      }else if(!root.getAbsolutePath.equals(other.root.getAbsolutePath)) return false
      true
    }

    override def toString = this.path + ";" + this.root.toString + ";" + this.hash
  }

  //TODO: run the produced jar through this adapter so it collects static info
  class GenerateMapAdapter(val writer: BufferedWriter) extends ClassVisitor(Opcodes.ASM4) {
    var className: String = _

    override def visit(version: Int, access: Int, name: String, signature: String, supername: String, interfaces: Array[String]){
      this.className = name
      super.visit(version, access, name, signature, supername, interfaces)
    }

    override def visitMethod(access: Int, name: String, desc: String, signature: String, exceptions: Array[String]): MethodVisitor = {
      if(name == "<clinit>") return super.visitMethod(access, name, desc, signature, exceptions)
      val clsSig = this.className + "/" + name + desc

      try{
        if((access & Opcodes.ACC_STATIC) == Opcodes.ACC_STATIC){
          writer.write(clsSig)
          writer.newLine()
        }
      }catch{
        case e: IOException => Throwables.propagate(e)
      }
      super.visitMethod(access, name, desc, signature, exceptions)
    }
  }
}
