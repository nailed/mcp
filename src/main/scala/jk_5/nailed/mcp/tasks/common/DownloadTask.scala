package jk_5.nailed.mcp.tasks.common

import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

import java.io._
import java.net.URL
import jk_5.nailed.mcp.tasks.common.CachedTask.Cached
import jk_5.nailed.mcp.delayed.{DelayedFile, DelayedString}

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

    import sys.process._
    (new URL(getUrl) #> outputFile).!!

    getLogger.info("Download complete", new Array[AnyRef](0))
  }

  def getOutput: File = output.call()
  def getUrl: String = url.call()
  def setOutput(output: DelayedFile) = this.output = output
  def setUrl(url: DelayedString) = this.url = url
}
