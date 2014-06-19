package jk_5.nailed.mcp.json

import com.google.gson.TypeAdapter
import java.io.File
import com.google.gson.stream.{JsonWriter, JsonReader}

/**
 * No description given
 *
 * @author jk-5
 */
class FileAdapter extends TypeAdapter[File] {

  override def read(json: JsonReader): File = {
    if(json.hasNext){
      val v = json.nextString()
      if(v == null) null else new File(v)
    }else null
  }

  override def write(json: JsonWriter, file: File) = if(file == null){
    json.nullValue()
  }else{
    json.value(file.getCanonicalPath)
  }
}
