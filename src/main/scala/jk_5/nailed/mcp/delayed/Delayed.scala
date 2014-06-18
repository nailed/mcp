package jk_5.nailed.mcp.delayed

import groovy.lang.Closure
import org.gradle.api.Project

/**
 * No description given
 *
 * @author jk-5
 */
abstract class Delayed[T](val pattern: String, val owner: Project) extends Closure[T](owner) {

  protected var resolved: Option[T] = None

  def get = resolved

  override def call(): T
  override def toString = call().toString
}
