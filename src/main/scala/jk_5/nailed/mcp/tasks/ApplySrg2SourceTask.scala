package jk_5.nailed.mcp.tasks

import java.io.{File, PrintStream}
import java.util

import com.google.common.base.{Charsets, Strings}
import com.google.common.collect.Lists
import com.google.common.io.Files
import jk_5.nailed.mcp.delayed.DelayedFile
import jk_5.nailed.mcp.io.SequencedInputSupplier
import net.minecraftforge.srg2source.rangeapplier.RangeApplier
import net.minecraftforge.srg2source.util.io._
import org.gradle.api.DefaultTask
import org.gradle.api.file.FileCollection

import scala.collection.convert.wrapAsScala._
import scala.collection.mutable
import org.gradle.api.tasks._

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
  @Optional @InputFiles private var staticsList: DelayedFile = _

  @TaskAction def doTask(){
    val ins = getIn
    val out = getOut
    val rangemap = getRangeMap
    val rangelog = File.createTempFile("rangelog", ".txt", this.getTemporaryDir)
    val srg = getSrgs
    var exc: FileCollection = getExcs

    if(ins.size == 0) return
    val inputSupplier = if(ins.size == 1) getInputSupplier(ins(0)) else {
      val s = new SequencedInputSupplier
      ins.foreach(i => s.add(getInputSupplier(i)))
      s
    }

    val outputSupplier = if(ins.size == 1 && ins.get(0) == out && inputSupplier.isInstanceOf[FolderSupplier]){
      inputSupplier.asInstanceOf[OutputSupplier]
    }else getOutputSupplier(out)

    if(getStaticsList != null){
      exc = generateDefaultExc(getStaticsList, exc, srg)
    }

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

  private def generateDefaultExc(modifiers: File, currentExc: FileCollection, srg: FileCollection): FileCollection = {
    if(modifiers == null || !modifiers.exists()){
      return currentExc
    }
    val static = mutable.HashSet[String]()
    try{
      getLogger.debug("  Reading Modifiers", new Array[AnyRef](0))
      for(l <- Files.readLines(modifiers, Charsets.UTF_8)){
        if(!Strings.isNullOrEmpty(l) && !l.startsWith("#")){
          static += l
        }
      }

      val temp = new File(this.getTemporaryDir, "generated.exc")
      if(temp.exists()) temp.delete()
      temp.getParentFile.mkdirs()
      temp.createNewFile()
      val writer = Files.newWriter(temp, Charsets.UTF_8)
      for(f <- srg){
        getLogger.debug("  Reading SRG: " + f.getCanonicalPath, new Array[AnyRef](0))
        for(l <- Files.readLines(f, Charsets.UTF_8)){
          if(!Strings.isNullOrEmpty(l) && !l.startsWith("#")){
            val typ = l.substring(0, 2)
            val info = l.substring(4)
            val pts = info.split(" ")
            if(typ == "MD"){
              var name = pts(2).substring(pts(2).lastIndexOf('/') + 1)
              if(name.startsWith("func_")){
                val isStatic = static.contains(pts(2) + pts(3))
                getLogger.debug("    MD: " + info, new Array[AnyRef](0))
                name = name.substring(5, name.indexOf('_', 5))
                val params = mutable.ArrayBuffer[String]()

                var idx = if(isStatic) 0 else 1
                getLogger.debug("      Name: " + name + " idx: " + idx + " static: " + isStatic, new Array[AnyRef](0))
                var i = 0
                var inArray = false
                while(i < pts(3).length){
                  val c = pts(3).charAt(i)
                  c match {
                    case '(' => //Start
                    case ')' => //End
                      i = pts(3).length
                    case '[' => //Array
                      inArray = true
                    case 'L' => //Class
                      val right = pts(3).substring(i)
                      val cname = right.substring(1, right.indexOf(';'))
                      i += cname.length + 1
                      params += "p_" + name + "_" + {idx += 1; idx-1} + "_"
                      inArray = false
                    case 'B' | 'C' | 'D' | 'F' | 'I' | 'J' | 'S' | 'Z' =>
                      params += "p_" + name + "_" + {idx += 1; idx-1} + "_"
                      if((c == 'D' || c == 'J') && !inArray) idx += 1
                      inArray = false
                    case _ => throw new IllegalArgumentException("Unrecognized type in method descriptor: " + c)
                  }
                  i += 1
                }

                if(params.length > 0){
                  writer.write(pts(2).substring(0, pts(2).lastIndexOf('/')))
                  writer.write('.')
                  writer.write(pts(2).substring(pts(2).lastIndexOf('/') + 1))
                  writer.write(pts(3))
                  writer.write("=|")
                  writer.write(params.mkString(","))
                  writer.newLine()
                }
              }
            }
          }
        }
      }
      writer.close()

      val files = mutable.ArrayBuffer[File]()
      files += temp //Make sure the new file is the first in the list so others can override
      files ++= currentExc
      return getProject.files(files.toArray)
    }catch{
      case e: Exception => throw new RuntimeException(e)
    }
    null
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
  def setStaticsList(exc: DelayedFile) = this.staticsList = exc

  def setRangeMap(rangeMap: DelayedFile) = this.rangeMap = rangeMap
  def setOutput(output: DelayedFile) = this.out = output

  def getRangeMap = this.rangeMap.call()
  def getExcs = getProject.files(exc)
  def getSrgs = getProject.files(srg)
  def getOut = this.out.call()
  @InputFiles def getIns = getProject.files(in)
  def getStaticsList = this.staticsList.call()
}
