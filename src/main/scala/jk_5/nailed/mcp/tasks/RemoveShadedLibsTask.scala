package jk_5.nailed.mcp.tasks

import jk_5.nailed.mcp.tasks.common.CachedTask
import org.gradle.api.tasks.{TaskAction, OutputFile, InputFile}
import jk_5.nailed.mcp.delayed.DelayedFile
import jk_5.nailed.mcp.tasks.common.CachedTask.Cached
import scala.collection.mutable
import scala.collection.convert.wrapAsScala._
import com.google.common.io.{ByteStreams, Files}
import com.google.common.base.Charsets
import java.util.zip.{ZipEntry, ZipOutputStream, ZipFile}
import java.io.{FileOutputStream, BufferedOutputStream}

/**
 * No description given
 *
 * @author jk-5
 */
class RemoveShadedLibsTask extends CachedTask {

  @InputFile private var config: DelayedFile = _
  @InputFile private var inJar: DelayedFile = _
  @OutputFile @Cached private var outJar: DelayedFile = _

  private val remove = mutable.HashSet[String]()

  @TaskAction def doTask(){
    try{
      for(l <- Files.readLines(this.config.call(), Charsets.UTF_8)){
        remove.add(l.split("#")(0).trim)
      }
    }catch{
      case e: Exception =>
        getLogger.error("Error while reading removeClasses.cfg: {}", e.getMessage)
        throw new RuntimeException(e)
    }

    var inFile: ZipFile = null
    var outStream: ZipOutputStream = null
    try{
      inFile = new ZipFile(this.inJar.call())
      outStream = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(this.outJar.call())))

      for(e <- inFile.entries()){
        val name = e.getName
        if(!name.endsWith(".class") || name.startsWith(".") || !this.remove.exists(n => name.startsWith(n))){
          val newEntry = new ZipEntry(name)
          outStream.putNextEntry(newEntry)
          outStream.write(ByteStreams.toByteArray(inFile.getInputStream(e)))
        }
      }
    }finally{
      if(inFile != null) inFile.close()
      if(outStream != null) outStream.close()
    }
  }

  def setInJar(inJar: DelayedFile) = this.inJar = inJar
  def setOutJar(outJar: DelayedFile) = this.outJar = outJar
  def setConfig(config: DelayedFile) = this.config = config

  def getInJar = this.inJar.call()
  def getOutJar = this.outJar.call()
  def getConfig = this.config.call()
}
