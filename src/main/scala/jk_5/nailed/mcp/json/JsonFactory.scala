package jk_5.nailed.mcp.json

import com.google.gson.{JsonParser, GsonBuilder}
import java.util
import java.util.Date
import java.io.{FileReader, File}
import scala.collection.convert.wrapAsScala._

/**
 * No description given
 *
 * @author jk-5
 */
object JsonFactory {

  final val gson = new GsonBuilder()
    .registerTypeAdapterFactory(new EnumAdapterFactory)
    .registerTypeAdapter(classOf[Date], new DateAdapter)
    .registerTypeAdapter(classOf[File], new FileAdapter)
    .enableComplexMapKeySerialization()
    .setPrettyPrinting()
    .create()

  def loadExcepterJson(file: File): util.LinkedHashMap[String, ExceptorEntry] = {
    val reader = new FileReader(file)
    val ret = new util.LinkedHashMap[String, ExceptorEntry]()
    val obj = new JsonParser().parse(reader).getAsJsonObject
    reader.close()
    for(e <- obj.entrySet()){
      ret.put(e.getKey, gson.fromJson(e.getValue, classOf[ExceptorEntry]))
    }
    ret
  }
}
