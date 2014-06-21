package jk_5.nailed.mcp.tasks

import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

import java.io._
import java.net.{HttpURLConnection, URL}
import CachedTask.Cached
import jk_5.nailed.mcp.delayed.{DelayedFile, DelayedString}
import com.google.common.io.ByteStreams

/**
 * No description given
 *
 * @author jk-5
 */
class DownloadTask extends CachedTask {

  @Input private var url: DelayedString = _
  @OutputFile @Cached private var output: DelayedFile = _

  @TaskAction def doTask() {
    val outputFile = getProject.file(getOutput)
    outputFile.getParentFile.mkdirs()
    outputFile.createNewFile()

    getLogger.info("Downloading {} to {}", getUrl, outputFile)
    getLogger.lifecycle("Download {}", getUrl)

    val connection = new URL(getUrl).openConnection.asInstanceOf[HttpURLConnection]
    connection.setInstanceFollowRedirects(true)

    val inStream = connection.getInputStream
    val outStream = new FileOutputStream(outputFile)

    ByteStreams.copy(inStream, outStream)

    inStream.close()
    outStream.flush()
    outStream.close()

    getLogger.info("Download complete", new Array[AnyRef](0))
  }

  def getOutput: File = output.call()
  def getUrl: String = url.call()
  def setOutput(output: DelayedFile) = this.output = output
  def setUrl(url: DelayedString) = this.url = url
}
