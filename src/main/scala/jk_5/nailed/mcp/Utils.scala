package jk_5.nailed.mcp

import java.io.File
import java.io.FileInputStream
import java.util
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import au.com.bytecode.opencsv.{CSVParser, CSVReader}
import com.google.common.io.Files
import java.nio.charset.Charset

/**
 * No description given
 *
 * @author jk-5
 */
object Utils {

  final val UTF_8 = Charset.forName("UTF-8")

  def hash(file: File): String =
    if(file.getPath.endsWith(".zip") || file.getPath.endsWith(".jar")) hashZip(file, Constants.HASH_FUNC)
    else hash(file, Constants.HASH_FUNC)

  def hashAll(file: File): util.List[String] = {
    val list = new util.ArrayList[String]()
    if(file.isDirectory){
      for(f <- file.listFiles){
        hashAll(f)
      }
    }else{
      list.add(hash(file))
    }
    list
  }

  def hash(file: File, function: String): String = {
    try{
      val fis = new FileInputStream(file)
      val array = Stream.continually(fis.read).takeWhile(-1 !=).map(_.toByte).toArray
      fis.close()
      return hash(array, function)
    }catch{
      case e: Exception => e.printStackTrace()
    }
    null
  }

  def hashZip(file: File, function: String): String = {
    try{
      val hasher = MessageDigest.getInstance(function)
      val zin = new ZipInputStream(new FileInputStream(file))
      var entry = zin.getNextEntry
      while(entry != null){
        hasher.update(entry.getName.getBytes)
        hasher.update(Stream.continually(zin.read()).takeWhile(-1 !=).map(_.toByte).toArray)
        entry = zin.getNextEntry
      }
      zin.close()
      val hash = hasher.digest
      var result = ""
      for(i <- 0 until hash.length){
        result += Integer.toString((hash(i) & 0xff) + 0x100, 16).substring(1)
      }
      result
    }catch{
      case e: Exception =>
        e.printStackTrace()
        null
    }
  }

  @inline def hash(str: String): String = hash(str.getBytes)
  @inline def hash(bytes: Array[Byte]): String = hash(bytes, Constants.HASH_FUNC)

  def hash(bytes: Array[Byte], function: String): String = {
    try{
      val complete = MessageDigest.getInstance(function)
      val hash = complete.digest(bytes)
      var result = ""
      for(i <- 0 until hash.length){
        result += Integer.toString((hash(i) & 0xff) + 0x100, 16).substring(1)
      }
      result
    }catch{
      case e: Exception =>
        e.printStackTrace()
        null
    }
  }

  def newCsvReader(file: File) = new CSVReader(Files.newReader(file, UTF_8), CSVParser.DEFAULT_SEPARATOR, CSVParser.DEFAULT_QUOTE_CHARACTER, CSVParser.DEFAULT_ESCAPE_CHARACTER, 1, false)
}
