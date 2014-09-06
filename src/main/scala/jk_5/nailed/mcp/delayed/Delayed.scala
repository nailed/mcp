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
  private var isResolved = false

  def get = resolved

  def resolve(): T

  override final def call(): T = {
    if(!isResolved){
      resolved = Option(resolve())
      isResolved = resolved.isDefined
    }
    resolved.get
  }

  override def toString = call().toString
}
