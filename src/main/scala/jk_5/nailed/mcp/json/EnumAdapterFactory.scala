package jk_5.nailed.mcp.json

import com.google.gson.{TypeAdapter, Gson, TypeAdapterFactory}
import com.google.gson.reflect.TypeToken
import java.util
import com.google.common.collect.Maps
import com.google.gson.stream.{JsonToken, JsonWriter, JsonReader}

/**
 * No description given
 *
 * @author jk-5
 */
class EnumAdapterFactory extends TypeAdapterFactory {

  override def create[T](gson: Gson, typ: TypeToken[T]): TypeAdapter[T] = {
    if(!typ.getRawType.isEnum) return null

    val map: util.Map[String, T] = Maps.newHashMap()
    typ.getRawType.getEnumConstants.foreach(c => {
      map.put(c.toString.toLowerCase, c.asInstanceOf[T])
    })

    new TypeAdapter[T]{
      override def read(reader: JsonReader): T = {
        if(reader.peek() == JsonToken.NULL){
          reader.nextNull()
          return null.asInstanceOf[T]
        }
        val name = reader.nextString()
        if(name == null) return null.asInstanceOf[T]
        map.get(name.toLowerCase)
      }

      override def write(writer: JsonWriter, value: T){
        if(value == null){
          writer.nullValue()
        }else{
          writer.value(value.toString.toLowerCase)
        }
      }
    }
  }
}
