package jk_5.nailed.mcp.patching

import java.util
import java.util.{Collections, Comparator, Locale}

import com.google.code.regexp.Pattern
import com.google.common.base.{Splitter, Strings}
import com.google.common.collect.Lists
import jk_5.nailed.mcp.Constants

import scala.collection.JavaConverters._
import scala.collection.convert.wrapAsScala._
import scala.collection.mutable

object ClassNameCleanup {

  final val METHOD_REG = Pattern.compile("^(?<indent>\\s+)(?<modifiers>(?:(?:" + FernFlowerPatcher.MODIFIERS + ") )*)(?:(?<return>[\\w\\[\\]\\.$]+) )?(?<name>[\\w$]+)\\((?<parameters>.*?)\\)(?<end>(?: throws (?<throws>[\\w$.]+(?:, [\\w$.]+)*))?)")
  final val CATCH_REG = Pattern.compile("catch \\((.*)\\)$")
  final val METHOD_DEC_END = Pattern.compile("(}|\\);|throws .+?;)$")
  final val CAPS_START = Pattern.compile("^[A-Z]")
  final val ARRAY = Pattern.compile("(\\[|\\.\\.\\.)")
  final val VAR_CALL = Pattern.compile("(?i)[a-z_$][a-z0-9_\\[\\]]+ var\\d+(?:x)*")
  final val VAR = Pattern.compile("var\\d+(?:x)*")
  final val COMPARATOR = new Comparator[String] {
    def compare(str1: String, str2: String) = str2.length - str1.length
  }

  def renameClass(text: String): String = {
    val lines = text.split("(\r\n|\r|\n)")
    val output = Lists.newArrayListWithCapacity[String](lines.length)
    var method: ClassNameCleanup.MethodInfo = null
    for(line <- lines){
      var matcher = METHOD_REG.matcher(line)
      val found = matcher.find
      if(!line.endsWith(";") && !line.endsWith(",") && found){
        method = new ClassNameCleanup.MethodInfo(method, matcher.group("indent"))
        method.lines += line
        var invalid = false
        val args = matcher.group("parameters")
        if(args != null){
          var break = false
          for(str <- Splitter.on(',').trimResults.omitEmptyStrings.split(args) if !break){
            if(str.indexOf(' ') == -1){
              invalid = true
              break = true
            }else{
              method.addVar(str)
            }
          }
        }
        if(invalid || METHOD_DEC_END.matcher(line).find){
          if(method.parent != null){
            method.parent.children.remove(method.parent.children.indexOf(method))
          }
          method = method.parent
          if(method == null){
            output.add(line)
          }
        }
      }else if(method != null && method.ENDING == line){
        method.lines += line
        if (method.parent == null) {
          for (l <- Splitter.on(Constants.NEWLINE).split(method.rename(null))) {
            output.add(l)
          }
        }
        method = method.parent
      }else if (method != null){
        method.lines += line
        matcher = CATCH_REG.matcher(line)
        if(matcher.find){
          method.addVar(matcher.group(1))
        }else{
          matcher = VAR_CALL.matcher(line)
          while (matcher.find) {
            val m = matcher.group
            if(!m.startsWith("return") && !m.startsWith("throw")){
              method.addVar(m)
            }
          }
        }
      }else{
        output.add(line)
      }
    }
    output.mkString(Constants.NEWLINE)
  }

  private class MethodInfo(val parent: ClassNameCleanup.MethodInfo, val indent: String) {

    var lines = mutable.ArrayBuffer[Any]()
    var vars = mutable.ArrayBuffer[String]()
    var children = mutable.ArrayBuffer[ClassNameCleanup.MethodInfo]()
    val ENDING = indent + "}"

    if (parent != null) {
      parent.children += this
      parent.lines += this
    }

    def addVar(info: String): Unit = vars += info

    def rename(n: ClassNameCleanup): String = {
      val namer = if (n == null) new ClassNameCleanup else new ClassNameCleanup(n)
      val renames = mutable.HashMap[String, String]()
      val unnamed = mutable.HashMap[String, String]()
      for (v <- vars) {
        val split = v.split(" ")
        if (!split(1).startsWith("var")) {
          renames.put(split(1), namer.getName(split(0), split(1)))
        } else {
          unnamed.put(split(1), split(0))
        }
      }
      if (unnamed.size > 0) {
        val sorted = new util.ArrayList[String](unnamed.keySet.asJava)
        Collections.sort(sorted, new Comparator[String] {
          override def compare(o1: String, o2: String) =
            if (o1.length < o2.length) -1
            else if (o1.length > o2.length) 1
            else o1.compareTo(o2)
        })
        for (s <- sorted) {
          renames.put(s, namer.getName(unnamed.get(s) orNull, s))
        }
      }
      val buf = new StringBuilder
      for (line <- lines) line match {
        case l: ClassNameCleanup.MethodInfo =>
          buf.append(l.rename(namer))
          buf.append(Constants.NEWLINE)
        case l: String =>
          buf.append(l)
          buf.append(Constants.NEWLINE)
        case _ =>
      }
      var body = buf.toString()
      if (renames.size > 0) {
        val sortedKeys = new util.ArrayList[String](renames.keySet.asJava)
        Collections.sort(sortedKeys, COMPARATOR)
        for (key <- sortedKeys) {
          if (VAR.matcher(key).matches()) {
            body = body.replace(key, renames.get(key) orNull)
          }
        }
      }
      body.substring(0, body.length - Constants.NEWLINE.length)
    }
  }
}

class ClassNameCleanup(val parent: ClassNameCleanup = null) {

  val last: mutable.HashMap[String, Holder] =
    if(parent == null){
      mutable.HashMap[String, Holder](
        "byte" -> new Holder(0, false, "b"),
        "char" -> new Holder(0, false, "c"),
        "short" -> new Holder(1, false, "short"),
        "int" -> new Holder(0, true, "i", "j", "k", "l"),
        "boolean" -> new Holder(0, true, "flag"),
        "double" -> new Holder(0, false, "d"),
        "float" -> new Holder(0, true, "f"),
        "File" -> new Holder(1, true, "file"),
        "String" -> new Holder(0, true, "s"),
        "Class" -> new Holder(0, true, "oclass"),
        "Long" -> new Holder(0, true, "olong"),
        "Byte" -> new Holder(0, true, "obyte"),
        "Short" -> new Holder(0, true, "oshort"),
        "Boolean" -> new Holder(0, true, "obool"),
        "Package" -> new Holder(0, true, "opackage"),
        "Enum" -> new Holder(0, true, "oenum")
      )
    }else{
      val ret = mutable.HashMap[String, Holder]()
      for(e <- parent.last){
        val v = e._2
        ret.put(e._1, new Holder(v.id, v.skipZero, v.names))
      }
      ret
    }

  val remap: mutable.HashMap[String, String] = if(parent == null){
    mutable.HashMap[String, String](
      "long" -> "int"
    )
  }else{
    val ret = mutable.HashMap[String, String]()
    parent.remap.foreach(e => ret.put(e._1, e._2))
    ret
  }

  def getName(t: String, v: String): String = {
    var typ = t
    var findtype = typ
    while(findtype.contains("[][]")) findtype = findtype.replaceAll("\\[\\]\\[\\]", "[]")
    var index: String = if(last.contains(findtype)) findtype
      else if(last.contains(findtype.toLowerCase(Locale.ENGLISH))) findtype.toLowerCase(Locale.ENGLISH)
      else if(remap.contains(typ)) remap.get(typ).get
      else null
    if(Strings.isNullOrEmpty(index) && (ClassNameCleanup.CAPS_START.matcher(typ).find() || ClassNameCleanup.ARRAY.matcher(typ).find())){
      typ = typ.replace("...", "[]")
      while(typ.contains("[][]")) typ = typ.replaceAll("\\[\\]\\[\\]", "[]")
      var name = typ.toLowerCase(Locale.ENGLISH).replace(".", "")
      var skipZero = true
      if(Pattern.compile("\\[").matcher(typ).find()){
        skipZero = true
        name = "a" + name
        name = name.replace("[]", "").replace("...", "")
      }
      last.put(typ.toLowerCase(Locale.ENGLISH), new Holder(0, skipZero, name))
      index = typ.toLowerCase(Locale.ENGLISH)
    }
    if(Strings.isNullOrEmpty(index)){
      return typ.toLowerCase(Locale.ENGLISH)
    }
    val holder = last.get(index).get
    val id = holder.id
    val names = holder.names
    val amount = names.size
    val name = if(amount == 1){
        names.get(0) + (if(id == 0 && holder.skipZero) "" else id)
      }else{
        names.get(id % amount) + (if(id < amount && holder.skipZero) "" else id / amount)
      }
    holder.id += 1
    name
  }

  case class Holder(var id: Int, skipZero: Boolean, private val inNames: util.List[String]) {
    def this(id: Int, skipZero: Boolean, names: String*) = this(id, skipZero, util.Arrays.asList[String](names: _*))
    val names = Lists.newArrayList[String]()
    this.names.addAll(inNames)
  }
}
