package jk_5.nailed.mcp.tasks

import java.io.{BufferedWriter, File, FileWriter}
import java.net.{URL, URLClassLoader}
import java.util

import com.google.common.base.Charsets
import com.google.common.io.Files
import jk_5.nailed.mcp.delayed.DelayedFile
import jk_5.nailed.mcp.patching.ReobfuscationExceptor
import net.md_5.specialsource.provider.{ClassLoaderProvider, JarProvider, JointProvider}
import net.md_5.specialsource.{Jar, JarMapping, JarRemapper}
import org.gradle.api.DefaultTask
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.{InputFiles, TaskAction}

import scala.collection.convert.wrapAsScala._
import scala.collection.mutable

/**
 * No description given
 *
 * @author jk-5
 */
class ReobfuscateTask extends DefaultTask{

  private var outJar: DelayedFile = _
  private var preFFJar: DelayedFile = _
  private var srg: DelayedFile = _
  private var exc: DelayedFile = _
  private var reverse: Boolean = _
  private var methodCsv: DelayedFile = _
  private var fieldCsv: DelayedFile = _
  private var extraSrg = mutable.ArrayBuffer[String]()
  private var extraSrgFiles = mutable.ArrayBuffer[AnyRef]()

  @TaskAction def doTask(){
    val inJar = getProject.getTasks.getByName("deobfJar").property("archivePath").asInstanceOf[File]
    var srg = getSrg
    if(getExc != null){
      val exceptor = new ReobfuscationExceptor
      exceptor.inputJar = inJar
      exceptor.deobfJar = getPreFFJar
      exceptor.exceptorConfig = getExc
      exceptor.fieldCsv = getFieldCsv
      exceptor.methodCsv = getMethodCsv
      val outSrg = new File(this.getTemporaryDir, "reobf_cls.srg")
      exceptor.readConfiguration()
      exceptor.buildSrg(srg, outSrg)
      srg = outSrg
    }

    val writer = new BufferedWriter(new FileWriter(srg, true))
    extraSrg.foreach { l =>
      writer.write(l)
      writer.newLine()
    }
    writer.flush()
    writer.close()
    getLogger.debug("Obfuscating jar...", new Array[String](0))
    obfuscate(inJar, getProject.getTasks.getByName("compileJava").property("classpath").asInstanceOf[FileCollection], srg) //TODO: scala classpath shouldn't be needed. Add it here if stuff derps up
  }

  private def obfuscate(inJar: File, classpath: FileCollection, srg: File){
    val mapping = new JarMapping
    mapping.loadMappings(Files.newReader(srg, Charsets.UTF_8), null, null, reverse)

    getExtraSrgFiles.foreach(f => mapping.loadMappings(f))

    val remapper = new JarRemapper(null, mapping)
    val input = Jar.init(inJar)

    val inheritanceProviders = new JointProvider
    inheritanceProviders.add(new JarProvider(input))

    if(classpath != null){
      inheritanceProviders.add(new ClassLoaderProvider(new URLClassLoader(toUrls(classpath))))
    }

    mapping.setFallbackInheritanceProvider(inheritanceProviders)

    val out = getOutJar
    if(!out.getParentFile.exists()){ //Needed because SS doesn't create it.
      out.getParentFile.mkdirs()
    }

    remapper.remapJar(input, getOutJar)
  }

  def toUrls(collection: FileCollection): Array[URL] = {
    val builder = mutable.ArrayBuffer[URL]()
    for(f <- collection.getFiles){
      builder += f.toURI.toURL
    }
    builder.toArray
  }

  def setOutJar(outJar: DelayedFile) = this.outJar = outJar
  def setPreFFJar(preFFJar: DelayedFile) = this.preFFJar = preFFJar
  def setSrg(srg: DelayedFile) = this.srg = srg
  def setExc(exc: DelayedFile) = this.exc = exc
  def setReverse(reverse: Boolean) = this.reverse = reverse
  def setMethodCsv(methodCsv: DelayedFile) = this.methodCsv = methodCsv
  def setFieldCsv(fieldCsv: DelayedFile) = this.fieldCsv = fieldCsv
  def setExtraSrg(extraSrg: mutable.ArrayBuffer[String]) = this.extraSrg = extraSrg
  def addExtraSrgFile(extraSrgFile: AnyRef) = this.extraSrgFiles += extraSrgFile

  def getOutJar = this.outJar.call()
  def getPreFFJar = this.preFFJar.call()
  def getSrg = this.srg.call()
  def getExc = this.exc.call()
  def isReverse = this.reverse
  def getMethodCsv = this.methodCsv.call()
  def getFieldCsv = this.fieldCsv.call()
  def getExtraSrg = this.extraSrg

  @InputFiles
  def getExtraSrgFiles: FileCollection = {
    val files = new util.ArrayList[File](extraSrgFiles.size)

    for(obj <- getProject.files(extraSrgFiles.toArray)){
      val f = getProject.file(obj)

      if(f.isDirectory){
        for(nested <- getProject.fileTree(f)){
          if(Files.getFileExtension(f.getName).toLowerCase == "srg"){
            files.add(f.getAbsoluteFile)
          }
        }
      }else if(Files.getFileExtension(f.getName).toLowerCase == "srg"){
        files.add(f.getAbsoluteFile)
      }
    }
    getProject.files(files)
  }
}
