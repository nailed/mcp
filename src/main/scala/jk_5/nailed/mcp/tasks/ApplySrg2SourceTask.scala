package jk_5.nailed.mcp.tasks

import scala.collection.convert.wrapAsScala._
import org.gradle.api.DefaultTask
import com.google.common.collect.Lists
import org.gradle.api.tasks._
import jk_5.nailed.mcp.delayed.DelayedFile
import java.io.{PrintStream, File}
import java.util
import org.gradle.api.file.FileCollection
import net.minecraftforge.srg2source.util.io._
import jk_5.nailed.mcp.io.SequencedInputSupplier
import net.minecraftforge.srg2source.rangeapplier.RangeApplier

/**
 * No description given
 *
 * @author jk-5
 */
class ApplySrg2SourceTask extends DefaultTask {

  @InputFiles private val srg = Lists.newLinkedList[AnyRef]()
  @Optional @InputFiles private val exc = Lists.newLinkedList[AnyRef]()
  @InputFile private var rangeMap: DelayedFile = _
  private val in = Lists.newLinkedList[DelayedFile]()
  private var out: DelayedFile = _

  @TaskAction def doTask(){
    val ins = getIn
    val out = getOut
    val rangemap = getRangeMap
    val rangelog = File.createTempFile("rangelog", ".txt", this.getTemporaryDir)
    val srg = getSrgs
    val exc = getExcs

    if(ins.size == 0) return
    val inputSupplier = if(ins.size == 1) getInputSupplier(ins(0)) else {
      val s = new SequencedInputSupplier
      ins.foreach(i => s.add(getInputSupplier(i)))
      s
    }

    val outputSupplier = if(ins.size == 1 && ins.get(0) == out && inputSupplier.isInstanceOf[FolderSupplier]){
      inputSupplier.asInstanceOf[OutputSupplier]
    }else getOutputSupplier(out)

    applyRangeMap(inputSupplier, outputSupplier, srg, exc, rangemap, rangelog)

    inputSupplier.close()
    inputSupplier.close()
  }

  def applyRangeMap(inputSupplier: InputSupplier, outputSupplier: OutputSupplier, srg: FileCollection, exc: FileCollection, rangeMap: File, rangeLog: File){
    val applier = new RangeApplier().readSrg(srg.getFiles)
    //TODO: proper logging
    //val debug = new PrintStream(Constants.createLogger(getLogger(), LogLevel.DEBUG));
    val stream = new PrintStream(rangeLog)
    applier.setOutLogger(stream)
    if(!exc.isEmpty) applier.readParamMap(exc)
    applier.dumpRenameMap()
    applier.remapSources(inputSupplier, outputSupplier, rangeMap, false)
  }

  def getInputSupplier(file: File): InputSupplier =
    if(file.isDirectory) new FolderSupplier(file)
    else if(file.getName.endsWith(".zip") || file.getName.endsWith(".jar")){
      val s = new ZipInputSupplier
      s.readZip(file)
      s
    }else throw new IllegalArgumentException("Can only make rangeExtractor input supplier from a directory or a zip/jar")

  def getOutputSupplier(f: File): OutputSupplier =
    if(f.isDirectory) new FolderSupplier(f)
    else if(f.getPath.endsWith(".jar") || f.getPath.endsWith(".zip")) new ZipOutputSupplier(f)
    else throw new IllegalArgumentException("Can only make suppliers out of directories and zips right now!")

  def getIn: util.List[File] = {
    val ret = Lists.newLinkedList[File]()
    in.foreach(f => ret.add(f.call()))
    ret
  }

  @OutputFiles
  def getOuts: FileCollection = {
    val outFile = getOut
    if(outFile.isDirectory) getProject.fileTree(outFile)
    else getProject.files(outFile)
  }

  def addExc(exc: File) = this.exc.add(exc)
  def addExc(exc: String) = this.exc.add(exc)
  def addExc(exc: DelayedFile) = this.exc.add(exc)
  def addSrg(srg: File) = this.srg.add(srg)
  def addSrg(srg: String) = this.srg.add(srg)
  def addSrg(srg: DelayedFile) = this.srg.add(srg)
  def addInput(in: DelayedFile) = this.in.add(in)

  def setRangeMap(rangeMap: DelayedFile) = this.rangeMap = rangeMap
  def setOutput(output: DelayedFile) = this.out = output

  def getRangeMap = this.rangeMap.call()
  def getExcs = getProject.files(exc)
  def getSrgs = getProject.files(srg)
  def getOut = this.out.call()
  @InputFiles def getIns = getProject.files(in)
}
