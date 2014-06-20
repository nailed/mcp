package jk_5.nailed.mcp.tasks

import jk_5.nailed.mcp.tasks.common.CachedTask
import org.gradle.api.tasks.{OutputFile, TaskAction, InputFile}
import jk_5.nailed.mcp.delayed.DelayedFile
import java.io.{FileInputStream, FileOutputStream, File}
import groovy.lang.Closure
import org.gradle.process.JavaExecSpec
import jk_5.nailed.mcp.{NailedMCPExtension, Constants, Utils}
import java.util.zip.{ZipEntry, ZipOutputStream, ZipInputStream}
import scala.collection.convert.wrapAsScala._
import com.google.common.io.{Files, ByteStreams}
import com.google.common.base.{Joiner, Charsets}
import jk_5.nailed.mcp.patching.{ContextualPatch, FernFlowerPatcher}
import jk_5.nailed.mcp.patching.ContextualPatch.{PatchStatus, PatchReport, IContextProvider}
import java.util
import com.google.common.collect.{ArrayListMultimap, Lists}
import org.gradle.api.logging.LogLevel
import com.github.abrarsyed.jastyle.FileWildcardFilter
import jk_5.nailed.mcp.tasks.common.CachedTask.Cached

/**
 * No description given
 *
 * @author jk-5
 */
class DecompileTask extends CachedTask {

  @InputFile private var inJar: DelayedFile = _
  @InputFile private var fernFlowerJar: File = _
  private var patch: DelayedFile = _
  @OutputFile @Cached private var outJar: DelayedFile = _

  private val sourceMap = new util.HashMap[String, String]()
  private val resourceMap = new util.HashMap[String, Array[Byte]]()

  @TaskAction def doTask(){
    val ffoutput = new File(getTemporaryDir, getInJar.getName)

    getLogger.lifecycle("Applying FernFlower", new Array[String](0))
    decompile(getInJar, getTemporaryDir, getFernFlowerJar)
    readJar(ffoutput)

    getLogger.lifecycle("Applying MCP Patches", new Array[String](0))
    if(getPatch.isFile){
      applySingleMcpPatch(getPatch)
    }else{
      applyPatchDirectory(getPatch)
    }

    saveJar(getOutJar)
  }

  def decompile(inJar: File, output: File, fernFlower: File){
    getProject.javaexec(new Closure[JavaExecSpec](this){
      override def call(): JavaExecSpec = {
        val exec = getDelegate.asInstanceOf[JavaExecSpec]
        exec.args(
          fernFlower.getAbsolutePath,
          "-din=1",
          "-rbr=0",
          "-dgs=1",
          "-asc=1",
          "-log=ERROR",
          inJar.getAbsolutePath,
          output.getAbsolutePath
        )
        exec.setMain("-jar")
        exec.setWorkingDir(getTemporaryDir)
        exec.classpath(Utils.getClassPath)
        //exec.setStandardOutput(Constants.createLogger(getLogger, LogLevel.DEBUG))
        exec.setStandardOutput(new FileOutputStream(new File(getTemporaryDir, "fernflower.log")))
        exec.setMaxHeapSize("512M")
        exec
      }
      override def call(arguments: scala.Any): JavaExecSpec = call()
    })
  }

  def readJar(jar: File){
    val zin = new ZipInputStream(new FileInputStream(jar))
    val exten = getProject.getExtensions.getByName(Constants.MCP_EXTENSION_NAME).asInstanceOf[NailedMCPExtension]
    val fixInterfaces = exten.getMinecraftVersion != "1.7.2" //We want this after 1.7.2
    var entry = zin.getNextEntry
    while(entry != null){
      getLogger.info("Reading and processing {}", entry.getName)
      if(!entry.getName.contains("META-INF")){
        if(entry.isDirectory || !entry.getName.endsWith(".java")){
          resourceMap.put(entry.getName, ByteStreams.toByteArray(zin))
        }else{
          val str = FernFlowerPatcher.processFile(new File(entry.getName).getName, new String(ByteStreams.toByteArray(zin), Charsets.UTF_8), fixInterfaces)
          sourceMap.put(entry.getName, str)
        }
      }
      entry = zin.getNextEntry
    }
    zin.close()
  }

  def applySingleMcpPatch(patchFile: File){
    val patch = ContextualPatch.create(Files.toString(patchFile, Charsets.UTF_8), new McpPatchContextProvider(sourceMap))
    printPatchErrors(patch.patch(false))
  }

  def applyPatchDirectory(patchDir: File){
    val patches = ArrayListMultimap.create[String, File]()
    for(f <- patchDir.listFiles(new FileWildcardFilter("*.patch"))){
      patches.put(f.getName, f)
      for(e <- patchDir.listFiles(new FileWildcardFilter(f.getName + ".*"))){
        patches.put(f.getName, e)
      }
    }

    for(key <- patches.keySet()){
      val patch = findPatch(patches.get(key))
      if(patch == null){
        getLogger.lifecycle("Patch not found for set: " + key); //This should never happen, but whatever
      }else{
        printPatchErrors(patch.patch(false))
      }
    }
  }

  def findPatch(files: util.List[File]): ContextualPatch = {
    var patch: ContextualPatch = null
    var break = false
    for(f <- files if !break){
      patch = ContextualPatch.create(Files.toString(f, Charsets.UTF_8), new McpPatchContextProvider(sourceMap))
      val errors = patch.patch(true)
      val success = !errors.exists(r => !r.getStatus.isSuccess)
      if(success) break = true
    }
    patch
  }

  private class McpPatchContextProvider(private val fileMap: util.Map[String, String]) extends IContextProvider {

    final val STRIP = 1

    private def strip(t: String): String = {
      val target = t.replace('\\', '/')
      var index = 0
      for(i <- 0 until STRIP){
        index = target.indexOf('/', index) + 1
      }
      target.substring(index)
    }

    override def getData(t: String): util.List[String] = {
      val target = strip(t)
      if(fileMap.containsKey(target)){
        val lines = fileMap.get(target).split("\r\n|\r|\n")
        val ret = Lists.newArrayListWithCapacity[String](lines.length)
        lines.foreach(ret.add)
        return ret
      }
      null
    }

    override def setData(target: String, data: util.List[String]){
      fileMap.put(strip(target), Joiner.on(Constants.NEWLINE).join(data))
    }
  }

  def saveJar(output: File){
    val zout = new ZipOutputStream(new FileOutputStream(output))

    for(e <- resourceMap.entrySet()){
      zout.putNextEntry(new ZipEntry(e.getKey))
      zout.write(e.getValue)
      zout.closeEntry()
    }

    for(e <- sourceMap.entrySet()){
      zout.putNextEntry(new ZipEntry(e.getKey))
      zout.write(e.getValue.getBytes)
      zout.closeEntry()
    }

    zout.close()
  }

  def printPatchErrors(errors: util.List[PatchReport]){
    var fuzzed = false
    for(report <- errors){
      if(!report.getStatus.isSuccess){
        getLogger.log(LogLevel.ERROR, "Patching failed: " + report.getTarget, report.getFailure)
        report.getHunks.filter(!_.getStatus.isSuccess).foreach { h =>
          getLogger.error("Hunk {} failed!", h.getHunkID)
        }
        throw report.getFailure
      }else if (report.getStatus == PatchStatus.Fuzzed){
        getLogger.log(LogLevel.INFO, "Patching fuzzed: " + report.getTarget, report.getFailure)
        fuzzed = true

        report.getHunks.filter(!_.getStatus.isSuccess).foreach { h =>
          getLogger.error("Hunk {} fuzzed {}!", h.getHunkID, h.getFuzz)
        }
      }else getLogger.info("Patch succeeded: {}", report.getTarget)
    }
    if(fuzzed){
      getLogger.lifecycle("Patches Fuzzed!", new Array[String](0))
    }
  }

  def setInJar(inJar: DelayedFile) = this.inJar = inJar
  def setFernFlowerJar(fernFlowerJar: File) = this.fernFlowerJar = fernFlowerJar
  def setPatch(patch: DelayedFile) = this.patch = patch
  def setOutJar(outJar: DelayedFile) = this.outJar = outJar

  def getInJar = this.inJar.call()
  def getFernFlowerJar = this.fernFlowerJar
  def getPatch = this.patch.call()
  def getOutJar = this.outJar.call()
}
