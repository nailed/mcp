package jk_5.nailed.mcp.delayed

import java.io.File

import org.gradle.api.Project
import org.gradle.api.file.FileTree
import org.gradle.api.internal.file.collections.FileTreeAdapter

/**
 * No description given
 *
 * @author jk-5
 */
class DelayedString(pattern: String, project: Project) extends Delayed[String](pattern, project){
  override def resolve() = DelayedResolver.resolve(pattern, owner)
}
class DelayedFile(pattern: String, project: Project) extends Delayed[File](pattern, project){
  override def resolve() = project.file(DelayedResolver.resolve(pattern, owner))
}
class DelayedFileTree(pattern: String, project: Project, val zipTree: Boolean = false) extends Delayed[FileTree](pattern, project){
  override def resolve(): FileTree = if(zipTree){
    new FileTreeAdapter(new ZipFileTree(project.file(DelayedResolver.resolve(pattern, project))))
  }else{
    project.fileTree(DelayedResolver.resolve(pattern, project))
  }
}
