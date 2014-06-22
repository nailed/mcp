package jk_5.nailed.mcp.tasks

import java.io.{ByteArrayInputStream, File, InputStream, InputStreamReader}
import java.util.{Collections, Comparator}
import scala.collection.mutable
import scala.collection.convert.wrapAsScala._
import jk_5.nailed.mcp.delayed.DelayedFile
import net.minecraftforge.srg2source.util.io.{FolderSupplier, InputSupplier, ZipInputSupplier}
import org.gradle.api.DefaultTask
import org.gradle.api.file.{FileCollection, FileVisitDetails, FileVisitor}
import org.gradle.api.tasks.{Input, InputFiles, OutputDirectory, TaskAction}
import com.cloudbees.diff.{Diff, Hunk}
import com.google.common.base.Charsets
import com.google.common.io.{ByteStreams, Files}
import com.google.common.collect.Lists

/**
 * No description given
 *
 * @author jk-5
 */
class GeneratePatchesTask extends DefaultTask {

  @OutputDirectory private var patchDir: DelayedFile = _
  @InputFiles private var changed: DelayedFile = _
  @InputFiles private var original: DelayedFile = _
  @Input private var originalPrefix = ""
  @Input private var changedPrefix = ""
  private val created = mutable.HashSet[File]()

  @TaskAction def doTask(){
    this.created.clear()
    getPatchDir.mkdirs()
    processFiles(getInputSupplier(original.call()), getInputSupplier(changed.call()))
    removeOld(getPatchDir)
  }

  private def getInputSupplier(file: File): InputSupplier = {
    if(file.isDirectory) return new FolderSupplier(file)
    val ret = new ZipInputSupplier
    ret.readZip(file)
    ret
  }

  private def removeOld(dir: File){
    val directories = Lists.newArrayList[File]()
    val tree = getProject.fileTree(dir)
    tree.visit(new FileVisitor(){
      override def visitDir(dir: FileVisitDetails): Unit = directories.add(dir.getFile)
      override def visitFile(f: FileVisitDetails){
        val file = f.getFile
        if(!created.contains(file)){
          getLogger.info("Removed patch {}", f.getRelativePath)
          file.delete
        }
      }
    })

    //We want things sorted in reverse order, so that sub folders come before parents
    Collections.sort(directories, new Comparator[File](){
      override def compare(o1: File, o2: File): Int = {
        val r = o1.compareTo(o2)
        if(r < 0) 1 else if(r > 0) -1 else 0
      }
    })

    for(f <- directories){
      if(f.listFiles.length == 0){
        getLogger.info("Removed empty dir {}", f.getPath)
        f.delete()
      }
    }
  }

  def processFiles(original: InputSupplier, changed: InputSupplier){
    val paths = original.gatherAll("")
    for(path <- paths){
      val p = path.replace('\\', '/')
      val o = original.getInput(path)
      val c = changed.getInput(path)
      try{
        processFile(p, o, c)
      }finally{
        if(o != null) o.close()
        if(c != null) c.close()
      }
    }
  }

  def processFile(relative: String, original: InputStream, changed: InputStream){
    getLogger.debug("Diffing {}", relative)
    var rel = relative
    val patchFile = new File(getPatchDir, relative + ".patch")
    if(changed == null){
      getLogger.debug("    Changed File does not exist", new Array[AnyRef](0))
      return
    }

    //We have to cache the bytes because for some stupid reason, diff reads the stream twice
    val oData = ByteStreams.toByteArray(original)
    val cData = ByteStreams.toByteArray(changed)

    val diff = Diff.diff(new InputStreamReader(new ByteArrayInputStream(oData), Charsets.UTF_8), new InputStreamReader(new ByteArrayInputStream(cData), Charsets.UTF_8), false)

    if(!rel.startsWith("/")) rel = "/" + rel

    if(!diff.isEmpty){
      val unidiff = diff.toUnifiedDiff(originalPrefix + rel, changedPrefix + rel,
        new InputStreamReader(new ByteArrayInputStream(oData), Charsets.UTF_8),
        new InputStreamReader(new ByteArrayInputStream(cData), Charsets.UTF_8), 3).replace("\r\n", "\n").replace("\n" + Hunk.ENDING_NEWLINE + "\n", "\n")

      val olddiff = if(patchFile.exists) Files.toString(patchFile, Charsets.UTF_8) else ""

      if(olddiff != unidiff){
        getLogger.debug("Writing patch {}", patchFile)
        patchFile.getParentFile.mkdirs()
        Files.touch(patchFile)
        Files.write(unidiff, patchFile, Charsets.UTF_8)
      }else{
        getLogger.debug("Patch did not change", new Array[AnyRef](0))
      }
      created.add(patchFile)
    }
  }

  def setChangedPrefix(changedPrefix: String) = this.changedPrefix = changedPrefix
  def setOriginalPrefix(originalPrefix: String) = this.originalPrefix = originalPrefix
  def setPatchDir(patchDir: DelayedFile) = this.patchDir = patchDir
  def setOriginal(original: DelayedFile) = this.original = original
  def setChanged(changed: DelayedFile) = this.changed = changed

  def getChangedPrefix = this.changedPrefix
  def getOriginalPrefix = this.originalPrefix
  def getPatchDir = this.patchDir.call()
  def getOriginal: FileCollection = {
    val f = this.original.call()
    if(f.isDirectory) getProject.fileTree(f) else getProject.files(f)
  }
  def getChanged: FileCollection = {
    val f = this.changed.call()
    if(f.isDirectory) getProject.fileTree(f) else getProject.files(f)
  }
}
