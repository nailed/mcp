package jk_5.nailed.mcp.tasks

import jk_5.nailed.mcp.delayed.DelayedFile
import org.gradle.api.tasks.{TaskAction, OutputFile, InputFile}
import CachedTask.Cached
import scala.collection.mutable
import java.io.{FileOutputStream, FileInputStream, File}
import java.util.zip.ZipInputStream
import com.google.common.io.ByteStreams
import com.google.common.base.Charsets
import java.util.jar.{JarEntry, JarOutputStream}

/**
 * No description given
 *
 * @author jk-5
 */
abstract class EditJarTask extends CachedTask {

  @InputFile protected var inJar: DelayedFile = _
  @OutputFile @Cached protected var outJar: DelayedFile = _

  protected val sourceMap = mutable.HashMap[String, String]()
  protected val resourceMap = mutable.HashMap[String, Array[Byte]]()

  @TaskAction final def doTask(){
    beforeRead()
    processJar(getInJar)
    beforeWrite()
    writeJar(getOutJar)
    afterWrite()
  }

  private final def processJar(in: File){
    val zin = new ZipInputStream(new FileInputStream(in))
    var entry = zin.getNextEntry
    while(entry != null){
      if(!entry.getName.contains("META-INF")){
        if(entry.isDirectory || !entry.getName.endsWith(".java")){
          resourceMap.put(entry.getName, ByteStreams.toByteArray(zin))
        }else{
          val str = process(new String(ByteStreams.toByteArray(zin), Charsets.UTF_8))
          sourceMap.put(entry.getName, str)
        }
      }
      entry = zin.getNextEntry
    }
    zin.close()
  }

  private final def writeJar(out: File){
    val zout = new JarOutputStream(new FileOutputStream(out))
    for(e <- resourceMap){
      zout.putNextEntry(new JarEntry(e._1))
      zout.write(e._2)
      zout.closeEntry()
    }
    for(e <- sourceMap){
      zout.putNextEntry(new JarEntry(e._1))
      zout.write(e._2.getBytes(Charsets.UTF_8))
      zout.closeEntry()
    }
    zout.close()
  }

  protected def process(in: String): String
  protected def beforeRead() = {}
  protected def beforeWrite() = {}
  protected def afterWrite() = {}

  def setInJar(inJar: DelayedFile) = this.inJar = inJar
  def setOutJar(outJar: DelayedFile) = this.outJar = outJar
  def getInJar = this.inJar.call()
  def getOutJar = this.outJar.call()
}
