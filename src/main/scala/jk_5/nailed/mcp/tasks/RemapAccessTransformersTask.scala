package jk_5.nailed.mcp.tasks

import java.io.{File, FileWriter, PrintWriter}
import java.text.DateFormat
import java.util
import java.util.Date

import com.google.common.base.Charsets
import com.google.common.io.{Files, LineProcessor}
import jk_5.nailed.mcp.delayed.DelayedFile
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.{InputFile, InputFiles, OutputDirectory, TaskAction}

import scala.collection.convert.wrapAsScala._
import scala.collection.mutable

/**
 * No description given
 *
 * @author jk-5
 */
class RemapAccessTransformersTask extends DefaultTask {

  @InputFiles private val accessTransformers = new util.ArrayList[File]()
  @InputFile private var srg: DelayedFile = _
  @OutputDirectory private var outputDir: DelayedFile = _

  private val methods = mutable.HashMap[String, String]()
  private val fields = mutable.HashMap[String, String]()

  @TaskAction
  def doTask(){
    Files.readLines(srg.call(), Charsets.UTF_8, new LineProcessor[AnyRef]{
      override def processLine(line: String): Boolean = {
        val s = line.split(" ")
        s(0) match{
          case "PK:" => //I don't care about package mappings. They will be covered by MD and FD
          case "CL:" => //Same for these mappings
          case "FD:" =>
            val notchIdx = s(1).lastIndexOf('/')
            val srgIdx = s(2).lastIndexOf('/')
            fields.put(s(2).substring(0, srgIdx).replace('/', '.') + " " + s(2).substring(srgIdx + 1).replace('/', '.'), s(2).substring(0, notchIdx) + " " + s(2).substring(notchIdx + 1))
          case "MD:" => methods.put(s(3).replace('/', '.') + s(4), s(1) + " " + s(2))
          case _ =>
        }
        true
      }
      override def getResult = null
    })

    if(!getOutputDir.isDirectory) getOutputDir.mkdir()

    accessTransformers.foreach{ a =>
      val out = new File(outputDir.call(), a.getName)
      processAT(a, out)
    }
  }

  def processAT(input: File, output: File){
    if(output.isFile) output.delete()
    val writer = new PrintWriter(new FileWriter(output, true))
    writer.println("# DO NOT EDIT THIS ACCESS TRANSFORMER FILE")
    writer.println("# It is automatically generated and remapped by Nailed-MCP on " + DateFormat.getDateTimeInstance.format(new Date))
    writer.println("# If you need to edit this, edit the original SRG AccessTransformer file")
    writer.println()

    Files.readLines(input, Charsets.UTF_8, new LineProcessor[AnyRef]{
      override def processLine(line: String): Boolean = {
        val c = line.split("#", 2)
        val s = c(0).split(' ')
        if(s.length < 3) return true

        val fd = fields.get(s(1) + " " + s(2))
        if(fd.isDefined){
          //Write access
          writer.print(s(0))
          writer.print(' ')
          writer.print(fd.get)

          writer.print(" #FD: " + s(2))
          writer.println()
        }

        val md = methods.get(s(1) + " " + s(2))
        if(fd.isDefined){
          //Write access
          writer.print(s(0))
          writer.print(' ')
          writer.print(fd.get)

          writer.print(" #MD: " + s(2).substring(0, s(2).indexOf('(')))
          writer.println()
        }

        if(fd.isEmpty && md.isEmpty){
          getLogger.lifecycle("Missing AT mapping: " + c(0))
          writer.print("# Missing mapping: ")
          writer.println(c(0))
        }

        true
      }
      override def getResult = null
    })

    writer.flush()
    writer.close()
  }

  def addInput(accessTransformer: File) = this.accessTransformers.add(accessTransformer)
  def setSrg(srg: DelayedFile) = this.srg = srg
  def setOutputDir(outputDir: DelayedFile) = this.outputDir = outputDir

  def getSrg = this.srg.call()
  def getOutputDir = this.outputDir.call()
}
