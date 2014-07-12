package jk_5.nailed.mcp.delayed

import groovy.lang.Closure
import org.gradle.api.file.CopySpec

/**
 * No description given
 *
 * @author jk-5
 */
class CopyFilter(private val dir: String, private val filters: String*) extends Closure[AnyRef](null) {
  override def call(args: AnyRef*): AnyRef = {
    val spec = getDelegate.asInstanceOf[CopySpec]
    filters.foreach { s =>
      if(s.startsWith("!")) spec.exclude(s.substring(1)) else spec.include(s)
    }
    if(dir != null && !dir.isEmpty) spec.into(dir)
    null
  }
}
