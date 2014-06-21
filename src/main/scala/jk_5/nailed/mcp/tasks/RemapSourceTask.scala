package jk_5.nailed.mcp.tasks

import org.gradle.api.tasks.InputFile
import jk_5.nailed.mcp.delayed.DelayedFile
import scala.collection.{mutable, immutable}
import scala.collection.convert.wrapAsScala._
import jk_5.nailed.mcp.{Constants, Utils}
import java.util.regex.Pattern
import com.google.common.base.Strings

/**
 * No description given
 *
 * @author jk-5
 */
class RemapSourceTask extends EditJarTask {

  @InputFile private var methodCsv: DelayedFile = _
  @InputFile private var fieldCsv: DelayedFile = _
  @InputFile private var paramCsv: DelayedFile = _

  private var doesJavadocs = false
  private var addNoJavadocs = false

  private val methods = mutable.HashMap[String, immutable.HashMap[String, String]]()
  private val fields = mutable.HashMap[String, immutable.HashMap[String, String]]()
  private val params = mutable.HashMap[String, String]()

  private final val SRG_FINDER = Pattern.compile("(func_[0-9]+_[a-zA-Z_]+|field_[0-9]+_[a-zA-Z_]+|p_[\\w]+_\\d+_)([^\\w\\$])")
  private final val METHOD = Pattern.compile("^((?: {4})+|\\t+)(?:[\\w$.\\[\\]]+ )+(func_[0-9]+_[a-zA-Z_]+)\\(")
  private final val FIELD = Pattern.compile("^((?: {4})+|\\t+)(?:[\\w$.\\[\\]]+ )+(field_[0-9]+_[a-zA-Z_]+) *(?:=|;)")

  override protected def beforeRead(){
    for(s <- Utils.newCsvReader(getMethodCsv).readAll()){
      methods.put(s(0), immutable.HashMap[String, String](
        "name" -> s(1),
        "javadoc" -> s(3)
      ))
    }
    for(s <- Utils.newCsvReader(getFieldCsv).readAll()){
      fields.put(s(0), immutable.HashMap[String, String](
        "name" -> s(1),
        "javadoc" -> s(3)
      ))
    }
    for(s <- Utils.newCsvReader(getParamCsv).readAll()){
      params.put(s(0), s(1))
    }
  }

  override protected def process(in: String): String = {
    val newlines = mutable.ArrayBuffer[String]()
    for(l <- Utils.lines(in)){
      var line = l
      if(addNoJavadocs){
        newlines += replaceInLine(line)
      }else{
        val m = METHOD.matcher(line)
        if(m.find()){
          val name = m.group(2)
          if(methods.contains(name) && methods.get(name).get.contains("name")){
            var javadoc = methods.get(name).get.get("javadoc").getOrElse(null)
            if(!Strings.isNullOrEmpty(javadoc)){
              if(doesJavadocs){
                javadoc = buildJavadoc(m.group(1), javadoc, isMethod = true)
              }else{
                javadoc = m.group(1) + "// JAVADOC METHOD $$ " + name
              }
              insetAboveAnnotations(newlines, javadoc)
            }
          }
        }else if(line.trim().startsWith("// JAVADOC ")){
          val m = SRG_FINDER.matcher(line)
          if(m.find()){
            val indent = line.substring(0, line.indexOf("// JAVADOC"))
            val name = m.group()
            if(name.startsWith("func_")){
              methods.get(name) match {
                case Some(method) if !Strings.isNullOrEmpty(method.get("javadoc").getOrElse(null)) =>
                  line = buildJavadoc(indent, method.get("javadoc").get, isMethod = true)
                case _ =>
              }
            }else if(name.startsWith("field_")){
              fields.get(name) match {
                case Some(field) if !Strings.isNullOrEmpty(field.get("javadoc").getOrElse(null)) =>
                  line = buildJavadoc(indent, field.get("javadoc").get, isMethod = true)
                case _ =>
              }
            }
            if(line.endsWith(Constants.NEWLINE)){
              line = line.substring(0, line.length() - Constants.NEWLINE.length())
            }
          }
        }else{
          val m = FIELD.matcher(line)
          if(m.find()){
            val name = m.group(2)
            if(fields.contains(name)){
              fields.get(name).get.get("javadoc") match {
                case Some(javadoc) if !Strings.isNullOrEmpty(javadoc) =>
                  insetAboveAnnotations(newlines, if(doesJavadocs){
                    buildJavadoc(m.group(1), javadoc, isMethod = false)
                  }else{
                    m.group(1) + "// JAVADOC FIELD $$ " + name
                  })
                case _ =>
              }
            }
          }
        }
        newlines += replaceInLine(line)
      }
    }
    newlines.mkString(Constants.NEWLINE)
  }

  def insetAboveAnnotations(newlines: mutable.ArrayBuffer[String], javadoc: String){
    var back = 0
    while(newlines(newlines.size - 1 - back).trim().startsWith("@")){
      back += 1
    }
    newlines.insert(newlines.size - back, javadoc)
  }

  def replaceInLine(line: String): String = {
    val buf = new StringBuffer
    val matcher = SRG_FINDER.matcher(line)
    while(matcher.find()){
      var find = matcher.group(1)
      if(find.startsWith("p_")) find = params.get(find).getOrElse(null)
      else if (find.startsWith("func_")) find = findName(methods, find)
      else if (find.startsWith("field_")) find = findName(fields, find)
      if(find == null) find = matcher.group(1)
      matcher.appendReplacement(buf, find)
      buf.append(matcher.group(2))
    }
    matcher.appendTail(buf)
    buf.toString
  }

  def findName(map: mutable.HashMap[String, immutable.HashMap[String, String]], key: String): String = map.get(key) match {
    case Some(s) => s.get("name").get
    case _ => null
  }

  def buildJavadoc(indent: String, javadoc: String, isMethod: Boolean): String = {
    val builder = new StringBuilder
    if(javadoc.length() >= 70 || isMethod){
      val list = wrapText(javadoc, 120 - (indent.length() + 3))
      builder.append(indent)
      builder.append("/**")
      builder.append(Constants.NEWLINE)
      for(line <- list){
        builder.append(indent)
        builder.append(" * ")
        builder.append(line)
        builder.append(Constants.NEWLINE)
      }
      builder.append(indent)
      builder.append(" */")
      //builder.append(Constants.NEWLINE)
    }else{
      builder.append(indent)
      builder.append("/** ")
      builder.append(javadoc)
      builder.append(" */")
      //builder.append(Constants.NEWLINE)
    }
    builder.toString().replace(indent, indent)
  }

  def wrapText(text: String, len: Int): immutable.List[String] = {
    if(text == null) return List[String]()
    if(len <= 0) return List(text)
    if(text.length <= len) return List(text)
    val lines = mutable.ListBuffer[String]()
    val line = new StringBuilder
    val word = new StringBuilder
    for(c <- text.toCharArray) c match {
      case ' ' | ',' | '-' =>
        word.append(c)
        val n = if(Character.isWhitespace(c)) 1 else 0
        if(line.length + word.length - n > len){
          lines += line.toString
          line.delete(0, line.length)
        }
        line.append(word)
        word.delete(0, word.length)
      case _ => word.append(c)
    }
    if(word.length > 0){
      lines += line.toString
    }
    lines.toList
  }

  def setMethodCsv(methodCsv: DelayedFile) = this.methodCsv = methodCsv
  def setFieldCsv(fieldCsv: DelayedFile) = this.fieldCsv = fieldCsv
  def setParamCsv(paramCsv: DelayedFile) = this.paramCsv = paramCsv
  def getMethodCsv = this.methodCsv.call()
  def getFieldCsv = this.fieldCsv.call()
  def getParamCsv = this.paramCsv.call()

  def isDoesJavadocs = this.doesJavadocs
  def setDoesJavadocs(doJavadocs: Boolean) = this.doesJavadocs = doJavadocs
  def noJavadocs() = this.addNoJavadocs = true
}
