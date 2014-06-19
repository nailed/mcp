package jk_5.nailed.mcp.json

import java.util

/**
 * No description given
 *
 * @author jk-5
 */
case class ExceptorEntry(var enclosingMethod: EnclosingMethod, var innerClasses: util.List[InnerClass])
case class EnclosingMethod(var owner: String, var name: String, var desc: String)
case class InnerClass(var inner_class: String, var outer_class: String, var inner_name: String, var access: String, var start: String){
  def getAccess = Integer.parseInt(if(access == null) "0" else access, 16)
  def getStart = Integer.parseInt(if(start == null) "0" else start, 16)
}
