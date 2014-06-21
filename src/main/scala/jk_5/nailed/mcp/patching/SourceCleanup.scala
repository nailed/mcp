package jk_5.nailed.mcp.patching

import java.io.{StringReader, StringWriter}
import jk_5.nailed.mcp.Constants
import java.util.regex.{Matcher, Pattern}

/**
 * No description given
 *
 * @author jk-5
 */
object SourceCleanup {

  final val COMMENTS_TRAILING = Pattern.compile("(?m)[ \\t]+$")
  final val COMMENTS_NEWLINES = Pattern.compile("(?m)^(?:\\r\\n|\\r|\\n){2,}")

  final val CLEANUP_header = Pattern.compile("^\\s+"); // Remove extra whitespace at the start of the file
  final val CLEANUP_footer = Pattern.compile("\\s+$"); // Remove extra whitespace at the end of  thefile
  final val CLEANUP_trailing = Pattern.compile("(?m)[ \\t]+$"); // Remove trailing whitespace
  final val CLEANUP_package = Pattern.compile("(?m)^package ([\\w.]+);$"); // Find package
  final val CLEANUP_import = Pattern.compile("(?m)^import (?:([\\w.]*?)\\.)?(?:[\\w]+);(?:\\r\\n|\\r|\\n)"); // Package and class
  final val CLEANUP_newlines = Pattern.compile("(?m)^\\s*(?:\\r\\n|\\r|\\n){2,}"); // Remove repeated blank lines
  final val CLEANUP_ifstarts = Pattern.compile("(?m)(^(?![\\s{}]*$).+(?:\\r\\n|\\r|\\n))((?:[ \\t]+)if.*)"); // Add a newline before if statements
  final val CLEANUP_blockstarts = Pattern.compile("(?m)(?<=\\{)\\s+(?=(?:\\r\\n|\\r|\\n)[ \\t]*\\S)")
  final val CLEANUP_blockends = Pattern.compile("(?m)(?<=[;}])\\s+(?=(?:\\r\\n|\\r|\\n)\\s*})")
  final val CLEANUP_gl = Pattern.compile("\\s*\\/\\*\\s*GL_[^*]+\\*\\/\\s*")
  final val CLEANUP_unicode = Pattern.compile("'\\\\u([0-9a-fA-F]{4})'")
  final val CLEANUP_charval = Pattern.compile("Character\\.valueOf\\(('.')\\)")
  final val CLEANUP_maxD = Pattern.compile("1\\.7976[0-9]*[Ee]\\+308[Dd]")
  final val CLEANUP_piD = Pattern.compile("3\\.1415[0-9]*[Dd]")
  final val CLEANUP_piF = Pattern.compile("3\\.1415[0-9]*[Ff]")
  final val CLEANUP_2piD = Pattern.compile("6\\.2831[0-9]*[Dd]")
  final val CLEANUP_2piF = Pattern.compile("6\\.2831[0-9]*[Ff]")
  final val CLEANUP_pi2D = Pattern.compile("1\\.5707[0-9]*[Dd]")
  final val CLEANUP_pi2F = Pattern.compile("1\\.5707[0-9]*[Ff]")
  final val CLEANUP_3pi2D = Pattern.compile("4\\.7123[0-9]*[Dd]")
  final val CLEANUP_3pi2F = Pattern.compile("4\\.7123[0-9]*[Ff]")
  final val CLEANUP_pi4D = Pattern.compile("0\\.7853[0-9]*[Dd]")
  final val CLEANUP_pi4F = Pattern.compile("0\\.7853[0-9]*[Ff]")
  final val CLEANUP_pi5D = Pattern.compile("0\\.6283[0-9]*[Dd]")
  final val CLEANUP_pi5F = Pattern.compile("0\\.6283[0-9]*[Ff]")
  final val CLEANUP_180piD = Pattern.compile("57\\.295[0-9]*[Dd]")
  final val CLEANUP_180piF = Pattern.compile("57\\.295[0-9]*[Ff]")
  final val CLEANUP_2pi9D = Pattern.compile("0\\.6981[0-9]*[Dd]")
  final val CLEANUP_2pi9F = Pattern.compile("0\\.6981[0-9]*[Ff]")
  final val CLEANUP_pi10D = Pattern.compile("0\\.3141[0-9]*[Dd]")
  final val CLEANUP_pi10F = Pattern.compile("0\\.3141[0-9]*[Ff]")
  final val CLEANUP_2pi5D = Pattern.compile("1\\.2566[0-9]*[Dd]")
  final val CLEANUP_2pi5F = Pattern.compile("1\\.2566[0-9]*[Ff]")
  final val CLEANUP_7pi100D = Pattern.compile("0\\.21991[0-9]*[Dd]")
  final val CLEANUP_7pi100F = Pattern.compile("0\\.21991[0-9]*[Ff]")
  final val CLEANUP_185pi100D = Pattern.compile("5\\.8119[0-9]*[Dd]")
  final val CLEANUP_185pi100F = Pattern.compile("0\\.8119[0-9]*[Ff]")

  def stripComments(content: String): String = {
    val in = new StringReader(content)
    val out = new StringWriter
    var inComment = false
    var inString = false
    var ci = in.read()
    while(ci != -1){
      val c = ci.toChar
      c match {
        case '\\' =>
          out.write(c)
          out.write(in.read())
        case '\"' =>
          if(!inComment){
            out.write(c)
            inString = !inString
          }
        case '\'' =>
          if(!inComment){
            out.write(c)
            out.write(in.read())
            out.write(in.read())
          }
        case '*' =>
          val c2 = in.read().toChar
          if(inComment && c2 == '/'){
            inComment = false
            out.write(' ')
          }else{
            out.write(c)
            out.write(c2)
          }
        case '/' =>
          if(!inString){
            val c2 = in.read().toChar
            c2 match {
              case '/' =>
                var c3 = 0
                while(c3 != '\n' && c3 != '\r') c3 = in.read().toChar
                out.write(c3)
              case '*' =>
                inComment = true
              case _ =>
                out.write(c)
                out.write(c2)
            }
          }else{
            out.write(c)
          }
        case _ =>
          if(!inComment) out.write(c)
      }
      ci = in.read()
    }
    out.close()
    var c = out.toString
    c = COMMENTS_TRAILING.matcher(c).replaceAll("")
    c = COMMENTS_NEWLINES.matcher(c).replaceAll(Constants.NEWLINE)
    c
  }

  def fixImports(c: String): String = {
    var text = c
    val m = CLEANUP_package.matcher(text)
    if(m.find()){
      val pack = m.group(1)
      val match2 = CLEANUP_import.matcher(text)
      while(match2.find()){
        if(match2.group(1).equals(pack)){
          text = text.replace(match2.group, "")
        }
      }
    }
    text
  }

  def cleanup(c: String): String = {
    var text = c
    text = CLEANUP_header.matcher(text).replaceAll("")
    text = CLEANUP_footer.matcher(text).replaceAll("")
    text = CLEANUP_trailing.matcher(text).replaceAll("")
    text = CLEANUP_newlines.matcher(text).replaceAll(Constants.NEWLINE)
    text = CLEANUP_ifstarts.matcher(text).replaceAll("$1" + Constants.NEWLINE + "$2")
    text = CLEANUP_blockstarts.matcher(text).replaceAll("")
    text = CLEANUP_blockends.matcher(text).replaceAll("")
    text = CLEANUP_gl.matcher(text).replaceAll("")
    text = CLEANUP_maxD.matcher(text).replaceAll("Double.MAX_VALUE")

    val matcher = CLEANUP_unicode.matcher(text)
    var v: Int = 0
    val buffer = new StringBuffer(text.length())
    while(matcher.find()){
      v = Integer.parseInt(matcher.group(1), 16)
      // Work around the .replace('\u00a7', '$') call in MinecraftServer and a couple of '\u0000'
      if(v > 255){
        matcher.appendReplacement(buffer, Matcher.quoteReplacement("" + v))
      }
    }
    matcher.appendTail(buffer)
    text = buffer.toString

    text = CLEANUP_charval.matcher(text).replaceAll("$1") //TODO: Test this better. This might derp stuff up
    text = CLEANUP_piD.matcher(text).replaceAll("Math.PI")
    text = CLEANUP_piF.matcher(text).replaceAll("(float)Math.PI")
    text = CLEANUP_2piD.matcher(text).replaceAll("(Math.PI * 2D)")
    text = CLEANUP_2piF.matcher(text).replaceAll("((float)Math.PI * 2F)")
    text = CLEANUP_pi2D.matcher(text).replaceAll("(Math.PI / 2D)")
    text = CLEANUP_pi2F.matcher(text).replaceAll("((float)Math.PI / 2F)")
    text = CLEANUP_3pi2D.matcher(text).replaceAll("(Math.PI * 3D / 2D)")
    text = CLEANUP_3pi2F.matcher(text).replaceAll("((float)Math.PI * 3F / 2F)")
    text = CLEANUP_pi4D.matcher(text).replaceAll("(Math.PI / 4D)")
    text = CLEANUP_pi4F.matcher(text).replaceAll("((float)Math.PI / 4F)")
    text = CLEANUP_pi5D.matcher(text).replaceAll("(Math.PI / 5D)")
    text = CLEANUP_pi5F.matcher(text).replaceAll("((float)Math.PI / 5F)")
    text = CLEANUP_180piD.matcher(text).replaceAll("(180D / Math.PI)")
    text = CLEANUP_180piF.matcher(text).replaceAll("(180F / (float)Math.PI)")
    text = CLEANUP_2pi9D.matcher(text).replaceAll("(Math.PI * 2D / 9D)")
    text = CLEANUP_2pi9F.matcher(text).replaceAll("((float)Math.PI * 2F / 9F)")
    text = CLEANUP_pi10D.matcher(text).replaceAll("(Math.PI / 10D)")
    text = CLEANUP_pi10F.matcher(text).replaceAll("((float)Math.PI / 10F)")
    text = CLEANUP_2pi5D.matcher(text).replaceAll("(Math.PI * 2D / 5D)")
    text = CLEANUP_2pi5F.matcher(text).replaceAll("((float)Math.PI * 2F / 5F)")
    text = CLEANUP_7pi100D.matcher(text).replaceAll("(Math.PI * 7D / 100D)")
    text = CLEANUP_7pi100F.matcher(text).replaceAll("((float)Math.PI * 7F / 100F)")
    text = CLEANUP_185pi100D.matcher(text).replaceAll("(Math.PI * 185D / 100D)")
    text = CLEANUP_185pi100F.matcher(text).replaceAll("((float)Math.PI * 185F / 100F)")
    text
  }
}
