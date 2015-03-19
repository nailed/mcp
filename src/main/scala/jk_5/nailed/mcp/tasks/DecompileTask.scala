package jk_5.nailed.mcp.tasks

import java.io._
import java.util
import java.util.Collections
import java.util.regex.Pattern
import java.util.zip.{ZipEntry, ZipInputStream, ZipOutputStream}

import com.github.abrarsyed.jastyle.{ASFormatter, FileWildcardFilter, OptParser}
import com.google.common.base.{Charsets, Joiner}
import com.google.common.collect.{ArrayListMultimap, Lists}
import com.google.common.io.{ByteStreams, Files}
import groovy.lang.Closure
import jk_5.nailed.mcp.delayed.DelayedFile
import jk_5.nailed.mcp.patching.ContextualPatch.{IContextProvider, PatchReport, PatchStatus}
import jk_5.nailed.mcp.patching.{ClassNameCleanup, ContextualPatch, FernFlowerPatcher, SourceCleanup}
import jk_5.nailed.mcp.tasks.CachedTask.Cached
import jk_5.nailed.mcp.{Constants, NailedMCPExtension, Utils}
import org.gradle.api.logging.LogLevel
import org.gradle.api.tasks.{InputFile, OutputFile, TaskAction}
import org.gradle.process.JavaExecSpec

import scala.collection.convert.wrapAsScala._

/**
 * No description given
 *
 * @author jk-5
 */
class DecompileTask extends CachedTask {

  @InputFile private var inJar: DelayedFile = _
  @InputFile private var astyleConfig: DelayedFile = _
  private var patch: DelayedFile = _
  @OutputFile @Cached private var outJar: DelayedFile = _

  private val sourceMap = new util.HashMap[String, String]()
  private val resourceMap = new util.HashMap[String, Array[Byte]]()

  private final val BEFORE = Pattern.compile("(?m)((case|default).+(?:\\r\\n|\\r|\\n))(?:\\r\\n|\\r|\\n)")
  private final val AFTER  = Pattern.compile("(?m)(?:\\r\\n|\\r|\\n)((?:\\r\\n|\\r|\\n)[ \\t]+(case|default))")

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

    getLogger.lifecycle("Cleaning up & formatting sourcecode", new Array[String](0))
    applySourceCleanup(getAStyleConfig)

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

  def applySourceCleanup(asConfig: File){
    val formatter = new ASFormatter
    val parser = new OptParser(formatter)
    parser.parseOptionFile(asConfig)
    val files = new util.ArrayList[String](sourceMap.keySet())
    Collections.sort(files)
    for(f <- files){
      var content = sourceMap.get(f)

      //Remove comments
      content = SourceCleanup.stripComments(content)

      //Fix imports
      content = SourceCleanup.fixImports(content)

      //Various other source code cleanup and fixes
      content = SourceCleanup.cleanup(content)

      //Apply AStyle
      val reader = new StringReader(content)
      val writer = new StringWriter
      formatter.format(reader, writer)
      reader.close()
      writer.flush()
      writer.close()
      content = writer.toString

      //Fix switch formatting
      content = BEFORE.matcher(content).replaceAll("$1")
      content = AFTER.matcher(content).replaceAll("$1")
      content = ClassNameCleanup.renameClass(content)

      sourceMap.put(f, content)
    }
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
      }else if(report.getStatus == PatchStatus.Fuzzed){
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
  def setAStyleConfig(astyleConfig: DelayedFile) = this.astyleConfig = astyleConfig
  def setPatch(patch: DelayedFile) = this.patch = patch
  def setOutJar(outJar: DelayedFile) = this.outJar = outJar

  @InputFile
  def getFernFlowerJar = this.getProject.getConfigurations.getByName(Constants.FERNFLOWER_CONFIGURATION).getSingleFile
  def getInJar = this.inJar.call()
  def getAStyleConfig = this.astyleConfig.call()
  def getPatch = this.patch.call()
  def getOutJar = this.outJar.call()
}
