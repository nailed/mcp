package jk_5.nailed.mcp.tasks

import java.io._
import java.net.{HttpURLConnection, URL}
import java.util.{Calendar, Date}

import com.google.common.io.ByteStreams
import jk_5.nailed.mcp.Constants
import jk_5.nailed.mcp.delayed.DelayedFile
import org.gradle.api.specs.Spec
import org.gradle.api.tasks.{OutputFile, TaskAction}
import org.gradle.api.{DefaultTask, Task}

/**
 * No description given
 *
 * @author jk-5
 */
class DownloadMappingsTask extends DefaultTask {

  this.onlyIf(new Spec[Task] {
    override def isSatisfiedBy(t: Task): Boolean = {
      val cal = Calendar.getInstance
      cal.setTime(getLastDownloadTime)
      cal.add(Calendar.MINUTE, 30)
      cal.before(new Date) || System.getProperty("forceDownload", "false").equalsIgnoreCase("true")
    }
  })

  final val cacheFile = new File(this.getTemporaryDir, "lastdownload.txt")

  @OutputFile private var methodCsv: DelayedFile = null
  @OutputFile private var fieldCsv: DelayedFile = null
  private var paramCsv: DelayedFile = null

  @TaskAction
  def doTask(){
    if(methodCsv != null) download(Constants.MAPPINGS_URL_METHODS, methodCsv.call())
    if(fieldCsv != null) download(Constants.MAPPINGS_URL_FIELDS, fieldCsv.call())
    if(paramCsv != null) download(Constants.MAPPINGS_URL_PARAMS, paramCsv.call())
    updateLastDownloadTime()
  }

  def download(url: String, dest: File){
    var connection: HttpURLConnection = null
    var inStream: InputStream = null
    var outStream: OutputStream = null
    try{
      connection = new URL(url).openConnection.asInstanceOf[HttpURLConnection]
      connection.setInstanceFollowRedirects(true)
      inStream = connection.getInputStream
      outStream = new FileOutputStream(dest)
      ByteStreams.copy(inStream, outStream)
    }finally{
      if(inStream != null) inStream.close()
      if(inStream != null){
        outStream.flush()
        outStream.close()
      }
    }
  }

  def getLastDownloadTime: Date = {
    if(!cacheFile.exists()) return new Date(0)
    var reader: BufferedReader = null
    try{
      reader = new BufferedReader(new FileReader(cacheFile))
      new Date(reader.readLine().toLong)
    }catch{
      case e: Exception => new Date(0)
    }finally{
      if(reader != null) reader.close()
    }
  }

  def updateLastDownloadTime(){
    if(!cacheFile.getParentFile.exists()) cacheFile.mkdirs()
    var writer: PrintWriter = null
    try{
      writer = new PrintWriter(cacheFile)
      writer.println(System.currentTimeMillis())
    }catch{
      case e: Exception =>
    }finally{
      if(writer != null) writer.close()
    }
  }

  def setMethodCsv(methodCsv: DelayedFile) = this.methodCsv = methodCsv
  def setFieldCsv(fieldCsv: DelayedFile) = this.fieldCsv = fieldCsv
  def setParamCsv(paramCsv: DelayedFile) = this.paramCsv = paramCsv
  def getMethodCsv = if(this.methodCsv == null) null else this.methodCsv.call()
  def getFieldCsv = if(this.fieldCsv == null) null else this.fieldCsv.call()
  def getParamCsv = if(this.paramCsv == null) null else this.paramCsv.call()
}
