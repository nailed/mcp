package jk_5.nailed.mcp.tasks

import java.io.File
import java.util

import com.google.common.base.Charsets
import com.google.common.collect.Lists
import com.google.common.io.Files
import jk_5.nailed.mcp.Constants
import jk_5.nailed.mcp.delayed.DelayedFile
import jk_5.nailed.mcp.patching.ContextualPatch
import jk_5.nailed.mcp.patching.ContextualPatch.PatchStatus
import org.gradle.api.file.FileCollection
import org.gradle.api.logging.LogLevel
import org.gradle.api.tasks.{Input, InputFiles}

import scala.collection.convert.wrapAsScala._
import scala.collection.{immutable, mutable}

/**
 * No description given
 *
 * @author jk-5
 */
class PatchSourceJarTask extends EditJarTask {

  private val stages = mutable.ArrayBuffer[ResourceHolder]()
  private var provider: ContextProvider = _

  @Input private var maxFuzz = 0

  override protected def process(in: String) = in
  override protected def beforeRead(){
    this.provider = new ContextProvider(this.sourceMap)
  }

  override protected def beforeWrite(){
    for(stage <- this.stages){
      if(!stage.srcDirs.isEmpty){
        getLogger.lifecycle("Injecting {} files", stage.name)
        for(rel <- stage.getRelInjects){
          val relative = rel.getRelative
          if(!sourceMap.contains(relative) && !resourceMap.contains(relative)){
            if(relative.endsWith(".java")){
              sourceMap.put(relative, Files.toString(rel.file, Charsets.UTF_8))
            }else{
              resourceMap.put(relative, Files.asByteSource(rel.file).read())
            }
          }
        }
      }
      if(stage.patchDir != null){
        getLogger.lifecycle("Applying {} patches", stage.name)
        applyPatchStage(stage.name, stage.getPatchFiles)
      }
    }
  }

  def applyPatchStage(name: String, files: FileCollection){
    getLogger.info("Reading patches for stage {}", name)
    val patches = readPatches(files)
    var fuzzed = false
    getLogger.info("Applying patches for stage {}", name)
    var failure: Throwable = null

    for(patch <- patches){
      val errors = patch.patch.patch(false)
      for(report <- errors){
        if(!report.getStatus.isSuccess){
          val reject = patch.makeRejectFile
          if(reject.exists()) reject.delete()
          getLogger.log(LogLevel.ERROR, "Patching failed: {} {}", provider.strip(report.getTarget), report.getFailure.getMessage)
          var failed = 0
          for(hunk <- report.getHunks){
            if(!hunk.getStatus.isSuccess){
              failed += 1
              getLogger.error("  " + hunk.getHunkID + ": " + (if(hunk.getFailure != null) hunk.getFailure.getMessage else "") + " @ " + hunk.getIndex)
              Files.append("++++ REJECTED PATCH %d\n".format(hunk.getHunkID), reject, Charsets.UTF_8)
              Files.append(hunk.hunk.lines.mkString(Constants.NEWLINE), reject, Charsets.UTF_8)
              Files.append("\n++++ END PATCH\n", reject, Charsets.UTF_8)
            }else if(hunk.getStatus == PatchStatus.Fuzzed){
              getLogger.info("  " + hunk.getHunkID + " fuzzed " + hunk.getFuzz + "!", new Array[AnyRef](0))
            }
          }
          getLogger.log(LogLevel.ERROR, s"  $failed/${report.getHunks.size} failed")
          getLogger.log(LogLevel.ERROR, "  Rejects written to {}", reject.getAbsolutePath)
          if(failure == null) failure = report.getFailure
        }else if(report.getStatus == ContextualPatch.PatchStatus.Fuzzed){
          getLogger.log(LogLevel.INFO, "Patching fuzzed: {}", provider.strip(report.getTarget))
          fuzzed = true
          for(hunk <- report.getHunks){
            if(hunk.getStatus == PatchStatus.Fuzzed){
              getLogger.info("  {} fuzzed {}!", hunk.getHunkID, hunk.getFuzz)
            }
          }
          if(failure == null) failure = report.getFailure
        }else getLogger.info("Patch succeeded: {}", provider.strip(report.getTarget))
      }
    }
    if(fuzzed) getLogger.lifecycle("Patches Fuzzed!")
    if(failure != null){
      getLogger.warn("The patching failed. To fix it, you have to manually apply the rejected patches")
      getLogger.warn("Check the generated rejections file, manually patch the changes in into the code,")
      getLogger.warn("and run \'gradle generatePatches\' when you are done")
    }
  }

  def readPatches(files: FileCollection): immutable.List[PatchedFile] = {
    val ret = mutable.ListBuffer[PatchedFile]()
    for(file <- files) if(file.getPath.endsWith(".patch")) ret += readPatch(file)
    ret.toList
  }

  def readPatch(file: File): PatchedFile = {
    getLogger.debug("Reading patch file {}", file)
    new PatchedFile(file)
  }

  @InputFiles
  def getAllPatches: FileCollection = {
    var ret: FileCollection = null
    for(stage <- stages.filter(s => s.patchDir != null)){
      if(ret == null) ret = stage.getPatchFiles
      else ret = getProject.files(ret, stage.getPatchFiles)
    }
    ret
  }

  @InputFiles
  def getAllInjects: FileCollection = {
    var ret: FileCollection = null
    for(stage <- stages){
      if(ret == null) ret = stage.getInjects
      else ret = getProject.files(ret, stage.getInjects)
    }
    ret
  }

  def addStage(name: String, patchDir: DelayedFile, injects: DelayedFile*) = this.stages += new ResourceHolder(name, patchDir, util.Arrays.asList(injects: _*))
  def setMaxFuzz(maxFuzz: Int) = this.maxFuzz = maxFuzz
  def getMaxFuzz = this.maxFuzz

  class PatchedFile(val fileToPatch: File) {
    final val patch = ContextualPatch.create(Files.toString(fileToPatch, Charsets.UTF_8), provider).setAccessC14N(true).setMaxFuzz(getMaxFuzz)
    def makeRejectFile = new File(fileToPatch.getParentFile, fileToPatch.getName + ".rej")
  }

  class ContextProvider(files: mutable.HashMap[String, String]) extends ContextualPatch.IContextProvider {

    private final val strip = 3

    def strip(target: String): String = {
      val t = target.replace('\\', '/')
      var index = 0
      for(i <- 0 until strip) index = t.indexOf('/', index) + 1
      t.substring(index)
    }

    override def getData(target: String): util.List[String] = {
      val t = strip(target)
      if(files.contains(t)){
        val lines = files.get(t).get.split("\r\n|\r|\n")
        val ret = Lists.newArrayListWithCapacity[String](lines.size)
        lines.foreach(ret.add)
        return ret
      }
      null
    }

    override def setData(target: String, data: util.List[String]){
      files.put(strip(target), data.mkString(Constants.NEWLINE))
    }
  }

  class ResourceHolder(val name: String, val patchDir: DelayedFile, val srcDirs: util.List[DelayedFile] = Lists.newArrayListWithCapacity(0)){

    def getPatchFiles: FileCollection = {
      val patch = getProject.file(this.patchDir)
      if(patch.isDirectory) getProject.fileTree(patch)
      else if(patch.getPath.endsWith("zip") || patch.getPath.endsWith("jar")) getProject.zipTree(patch)
      else getProject.files(patch)
    }

    def getInjects: FileCollection = {
      val trees = Lists.newArrayListWithCapacity[FileCollection](srcDirs.size())
      srcDirs.foreach(f => trees.add(getProject.fileTree(f.call())))
      getProject.files(trees)
    }

    def getRelInjects: util.List[RelFile] = {
      val files = Lists.newLinkedList[RelFile]()
      for(df <- srcDirs){
        val dir = df.call()
        if(dir.isDirectory){
          for(f <- getProject.fileTree(dir)){
            files.add(new RelFile(f, dir))
          }
        }else{
          files.add(new RelFile(dir, dir.getParentFile))
        }
      }
      files
    }
  }

  case class RelFile(file: File, root: File) {
    def getRelative = file.getCanonicalPath.substring(root.getCanonicalPath.length + 1).replace('\\', '/')
  }
}
