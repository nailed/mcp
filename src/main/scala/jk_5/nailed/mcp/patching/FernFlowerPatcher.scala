package jk_5.nailed.mcp.patching

import java.util

import com.google.code.regexp.{Matcher, Pattern}
import com.google.common.base.Strings
import jk_5.nailed.mcp.{Constants, Utils}

import scala.collection.convert.wrapAsScala._

/**
 * No description given
 *
 * @author jk-5
 */
object FernFlowerPatcher {

  final val MODIFIERS = "public|protected|private|static|abstract|final|native|synchronized|transient|volatile|strictfp"
  final val SYNTHETICS = Pattern.compile("(?m)(\\s*// \\$FF: (synthetic|bridge) method(\\r\\n|\\n|\\r)){1,2}\\s*(?<modifiers>(?:(?:" + MODIFIERS + ") )*)(?<return>.+?) (?<method>.+?)\\((?<arguments>.*)\\)\\s*\\{(\\r\\n|\\n|\\r)\\s*return this\\.(?<method2>.+?)\\((?<arguments2>.*)\\);(\\r\\n|\\n|\\r)\\s*\\}")
  final val ABSTRACT = Pattern.compile("(?m)^(?<indent>[ \\t\\f\\v]*)(?<modifiers>(?:(?:" + MODIFIERS + ") )*)(?<return>[^ ]+) (?<method>func_(?<number>\\d+)_[a-zA-Z_]+)\\((?<arguments>([^ ,]+ var\\d+,? ?)*)\\)(?: throws (?:[\\w$.]+,? ?)+)?;$")
  final val TRAILING_WHITESPACE = "(?m)[ \\t]+$"
  final val REPEATED_NEWLINES = "(?m)^(\\r\\n|\\r|\\n){2,}"
  final val EMPTY_SUPER = "(?m)^[ \t]+super\\(\\);(\\r\\n|\\n|\\r)"
  final val TRAILINGZERO = "([0-9]+\\.[0-9]*[1-9])0+([DdFfEe])" //0.0010D => 0.001D - This is a small difference on OSX that might cause broken or fuzzed patches
  final val CLASS_REGEX = "(?<modifiers>(?:(?:" + MODIFIERS + ") )*)(?<type>enum|class|interface) (?<name>[\\w$]+)(?: (extends|implements) (?:[\\w$.]+(?:, [\\w$.]+)*))* \\{"
  final val ENUM_ENTRY_REGEX = "(?<name>[\\w$]+)\\(\"(?:[\\w$]+)\", [0-9]+(?:, (?<body>.*?))?\\)(?<end> *(?:;|,|\\{)$)"
  final val CONSTRUCTOR_REGEX = "(?<modifiers>(?:(?:" + MODIFIERS + ") )*)%s\\((?<parameters>.*?)\\)(?<end>(?: throws (?<throws>[\\w$.]+(?:, [\\w$.]+)*))? *(?:\\{\\}| \\{))"
  final val CONSTRUCTOR_CALL_REGEX = "(?<name>this|super)\\((?<body>.*?)\\)(?<end>;)"
  final val VALUE_FIELD_REGEX = "private static final %s\\[\\] [$\\w\\d]+ = new %s\\[\\]\\{.*?\\};"

  def processFile(name: String, c: String, fixInterfaces: Boolean): String = {
    var content = c
    val out = new StringBuffer
    val m = SYNTHETICS.matcher(content)
    while(m.find()) m.appendReplacement(out, syntheticReplacement(m).replace("$", "\\$"))
    m.appendTail(out)
    content = out.toString
    content = content.replaceAll(TRAILING_WHITESPACE, "")
    content = content.replaceAll(TRAILINGZERO, "$1$2")

    val lines = new util.ArrayList[String]()
    lines.addAll(Utils.lines(content))

    processClass(lines, "", 0, "", "")
    content = lines.mkString(Constants.NEWLINE)

    content = content.replaceAll(REPEATED_NEWLINES, Constants.NEWLINE)
    content = content.replaceAll(EMPTY_SUPER, "")

    if(fixInterfaces){
      val b = new StringBuffer
      val m1 = ABSTRACT.matcher(content)
      while(m1.find()) m1.appendReplacement(b, abstractReplacement(m1).replace("$", "\\$"))
      m1.appendTail(b)
      content = b.toString
    }
    content
  }

  def processClass(lines: util.List[String], indent: String, startIndex: Int, qualifiedName: String, simpleName: String): Int = {
    val classPattern = Pattern.compile(indent + CLASS_REGEX)
    var i = startIndex
    while(i < lines.size()){
      val line = lines.get(i)
      if(!Strings.isNullOrEmpty(line) && !line.startsWith("package") && !line.startsWith("import")){
        val matcher = classPattern.matcher(line)
        if(matcher.find()){
          var newIndent: String = null
          var classPath: String = null
          if(Strings.isNullOrEmpty(qualifiedName)){
            classPath = matcher.group("name")
            newIndent = indent
          }else{
            classPath = qualifiedName + "." + matcher.group("name")
            newIndent = indent + "   "
          }

          if(matcher.group("type").equals("enum")){
            processEnum(lines, newIndent, i + 1, classPath, matcher.group("name"))
          }

          i = processClass(lines, newIndent, i + 1, classPath, matcher.group("name"))
        }
        if(line.startsWith(indent + "}")) return i
      }
      i += 1
    }
    //0
    startIndex
  }

  def processEnum(lines: util.List[String], indent: String, startIndex: Int, qualifiedName: String, simpleName: String){
    val newIndent = indent + "   "
    val enumEntry = Pattern.compile("^" + newIndent + ENUM_ENTRY_REGEX)
    val constructor = Pattern.compile("^" + newIndent + String.format(CONSTRUCTOR_REGEX, simpleName))
    val constructorCall = Pattern.compile("^" + newIndent + "   " + CONSTRUCTOR_CALL_REGEX)
    val formatted = newIndent + String.format(VALUE_FIELD_REGEX, qualifiedName, qualifiedName)
    val valueField = Pattern.compile("^" + formatted)
    var newLine: String = null
    var prevSynthetic = false

    var i = startIndex
    var break = false
    while(i < lines.size() && !break){
      newLine = null
      val line = lines.get(i)

      var matcher = enumEntry.matcher(line)
      if(matcher.find()){
        var body = matcher.group("body")
        newLine = newIndent + matcher.group("name")
        if(!Strings.isNullOrEmpty(body)){
          var args = body.split(", ")
          if(line.endsWith("{")){
            if(args(args.length - 1) == "null"){
              args = util.Arrays.copyOf(args, args.length - 1)
            }
          }
          body = args.mkString(", ")
        }
        if(Strings.isNullOrEmpty(body)){
          newLine += matcher.group("end")
        }else{
          newLine += "(" + body + ")" + matcher.group("end")
        }
      }

      matcher = constructor.matcher(line)
      if(matcher.find()){
        val tmp = new StringBuilder
        tmp.append(newIndent)
        tmp.append(matcher.group("modifiers"))
        tmp.append(simpleName)
        tmp.append("(")

        val args = matcher.group("parameters").split(", ")
        for(i <- 2 until args.length){
          tmp.append(args(i)).append(if(i < args.length - 1) ", " else "")
        }
        tmp.append(")")
        tmp.append(matcher.group("end"))
        newLine = tmp.toString()
        if(args.length <= 2 && newLine.endsWith("}")) newLine = ""
      }

      matcher = constructorCall.matcher(line)
      if(matcher.find()){
        var body = matcher.group("body")
        if(!Strings.isNullOrEmpty(body)){
          var args = body.split(", ")
          args = util.Arrays.copyOfRange(args, 2, args.length)
          body = args.mkString(", ")
        }
        newLine = newIndent + "   " + matcher.group("name") + "(" + body + ")" + matcher.group("end")
      }

      if(prevSynthetic){
        matcher = valueField.matcher(line)
        if(matcher.find()){
          newLine = ""
        }
      }

      if(line.contains("// $FF: synthetic field")){
        newLine = ""
        prevSynthetic = true
      }else{
        prevSynthetic = false
      }

      if(newLine != null){
        lines.set(i, newLine)
      }

      if(line.startsWith(indent + "}")){
        break = true
      }
      i += 1
    }
  }

  //Remove all synthetic/bridge methods. The compiler will regenerate it anyway.
  def syntheticReplacement(m: Matcher): String = {
    //First remove bridge methods from one method to another with exactly the same name
    if(m.group("method") != m.group("method2")) return m.group()

    //Now we normalize the arguments list. If the arguments are the same it's a simple bridge method and we remove it
    val arg1 = m.group("arguments")
    val arg2 = m.group("arguments2")
    if(arg1 == arg2 && arg1 == "") return ""

    val args = m.group("arguments").split(", ")
    for(i <- 0 until args.length){
      args(i) = args(i).split(" ")(1)
    }

    val b = new StringBuilder
    b.append(args(0))
    for(i <- 1 until args.length){
      b.append(", ").append(args(i))
    }
    if(b.toString == arg2) return ""
    m.group()
  }

  def abstractReplacement(m: Matcher): String = {
    val original = m.group("arguments")
    val number = m.group("number")

    if(Strings.isNullOrEmpty(original)){
      return m.group()
    }

    val args = original.split(", ")
    val fixed = new StringBuilder
    for(i <- 0 until args.length){
      val p = args(i).split(" ")
      fixed.append(p(0))
      fixed.append(" p_")
      fixed.append(number)
      fixed.append('_')
      fixed.append(p(1).substring(3))
      fixed.append('_')
      if(i != args.length - 1){
        fixed.append(", ")
      }
    }
    m.group().replace(original, fixed.toString())
  }
}
