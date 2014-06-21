package jk_5.nailed.mcp.tasks

import groovy.lang.Closure
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.{InputFiles, Input, OutputDirectory, TaskAction}
import jk_5.nailed.mcp.delayed.DelayedFile
import org.apache.shiro.util.AntPathMatcher
import scala.collection.mutable
import java.util.zip.ZipFile
import java.io.File
import java.io.FileOutputStream
import com.google.common.io.ByteStreams
import jk_5.nailed.mcp.tasks.CachedTask.Cached

class ExtractTask extends CachedTask {
  
  final val antMatcher = new AntPathMatcher
  @InputFiles private final val sourcePaths = mutable.LinkedHashSet[DelayedFile]()
  @Input private final val excludes = mutable.ListBuffer[String]()
  @Input private final val excludeCalls = mutable.ListBuffer[Closure[Boolean]]()
  @Input private final val includes = mutable.ListBuffer[String]()
  @Input private var includeEmptyDirs = true
  @Cached @OutputDirectory private var destinationDir: DelayedFile = _

  @TaskAction def doTask(){
    if(!this.destinationDir.call().exists) destinationDir.call().mkdirs()
    for(source <- this.sourcePaths){
      val input = new ZipFile(source.call())
      try{
        val itr = input.entries()
        while(itr.hasMoreElements){
          val entry = itr.nextElement()
          if(shouldExtract(entry.getName)){
            val out = new File(this.destinationDir.call(), entry.getName)
            if(entry.isDirectory){
              if(this.includeEmptyDirs && !out.exists()) out.mkdirs()
            }else{
              val outParent = out.getParentFile
              if(!outParent.exists()) outParent.mkdirs()

              val fos = new FileOutputStream(out)
              val is = input.getInputStream(entry)

              ByteStreams.copy(is, fos)

              fos.close()
              is.close()
            }
          }
        }
      }finally{
        input.close()
      }
    }
  }

  private def shouldExtract(path: String): Boolean = {
    if(this.excludes.exists(e => this.antMatcher.matches(e, path))) return false
    if(this.excludeCalls.exists(e => e.call(path))) return false
    if(this.includes.exists(i => this.antMatcher.matches(i, path))) return true
    this.includes.size == 0
  }

  def from(paths: DelayedFile*): ExtractTask = {
    paths.foreach(p => sourcePaths += p)
    this
  }

  def into(target: DelayedFile): ExtractTask = {
    this.destinationDir = target
    this
  }
  
  def include(patterns: String*): ExtractTask = {
    patterns.foreach(p => this.includes += p)
    this
  }

  def exclude(patterns: String*): ExtractTask = {
    patterns.foreach(p => this.excludes += p)
    this
  }
  
  def exclude(c: Closure[Boolean]): ExtractTask = {
    this.excludeCalls += c
    this
  }
  
  def setDestinationDir(target: DelayedFile): ExtractTask = {
    this.destinationDir = target
    this
  }

  def setIncludeEmptyDirs(includeEmptyDirs: Boolean) = this.includeEmptyDirs = includeEmptyDirs

  def getDestinationDir = this.destinationDir.call()
  def getIncludes = this.includes
  def getExcludes = this.excludes
  def getExcludeCalls = this.excludeCalls
  def getSourcePaths: FileCollection = {
    var collection: FileCollection = getProject.files(new Array[AnyRef](0))
    this.sourcePaths.foreach(f => collection = collection.plus(getProject.files(f)))
    collection
  }
  def isIncludeEmptyDirs = this.includeEmptyDirs
  
  override def defaultCache(): Boolean = false
}

