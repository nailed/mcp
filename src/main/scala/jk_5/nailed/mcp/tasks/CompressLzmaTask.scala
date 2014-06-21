package jk_5.nailed.mcp.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.{TaskAction, InputFile, OutputFile}
import jk_5.nailed.mcp.delayed.DelayedFile
import java.io.{FileOutputStream, FileInputStream, BufferedInputStream}
import lzma.streams.LzmaOutputStream
import com.google.common.io.ByteStreams

/**
 * No description given
 *
 * @author jk-5
 */
class CompressLzmaTask extends DefaultTask {

  @InputFile private var input: DelayedFile = _
  @OutputFile private var output: DelayedFile = _

  @TaskAction def doTask(){
    val is = new BufferedInputStream(new FileInputStream(this.getInput))
    val os = new LzmaOutputStream.Builder(new FileOutputStream(this.getOutput)).useEndMarkerMode(true).build()
    ByteStreams.copy(is, os)
    is.close()
    os.close()
  }

  def setInput(input: DelayedFile) = this.input = input
  def setOutput(output: DelayedFile) = this.output = output
  def getInput = this.input.call()
  def getOutput = this.output.call()
}
