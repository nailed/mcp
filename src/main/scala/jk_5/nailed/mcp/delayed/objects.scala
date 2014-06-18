package jk_5.nailed.mcp.delayed

import org.gradle.api.Project
import java.io.File

/**
 * No description given
 *
 * @author jk-5
 */
class DelayedString(pattern: String, project: Project) extends Delayed[String](pattern, project){

  override def call() = resolved match {
    case Some(v) => v
    case None =>
      resolved = Some(DelayedResolver.resolve(pattern, owner))
      resolved.get
  }
}
class DelayedFile(pattern: String, project: Project) extends Delayed[File](pattern, project){

  override def call() = resolved match {
    case Some(v) => v
    case None =>
      resolved = Some(project.file(DelayedResolver.resolve(pattern, owner)))
      resolved.get
  }
}
