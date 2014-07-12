package jk_5.nailed.mcp.delayed

import java.io.{File, IOException, InputStream}
import java.util
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.{ZipEntry, ZipFile}

import org.gradle.api.file.{FileVisitDetails, FileVisitor, RelativePath}
import org.gradle.api.internal.file.AbstractFileTreeElement
import org.gradle.api.internal.file.collections.MinimalFileTree
import org.gradle.api.{GradleException, InvalidUserDataException, UncheckedIOException}
import org.gradle.util.SingleMessageLogger

class ZipFileTree(val zipFile: File) extends MinimalFileTree {

  def getDisplayName = "ZIP '%s'".format(zipFile)

  def visit(visitor: FileVisitor){
    if(!zipFile.exists){
      SingleMessageLogger.nagUserOfDeprecatedBehaviour("The specified zip file %s does not exist and will be silently ignored".format(getDisplayName))
      return
    }
    if(!zipFile.isFile){
      throw new InvalidUserDataException("Cannot expand %s as it is not a file.".format(getDisplayName))
    }
    val stopFlag = new AtomicBoolean
    try{
      val zip = new ZipFile(zipFile)
      try{
        val entriesByName = new util.TreeMap[String, ZipEntry]
        val entries = zip.entries
        while(entries.hasMoreElements){
          val entry = entries.nextElement
          entriesByName.put(entry.getName, entry)
        }
        val sortedEntries = entriesByName.values.iterator
        while(!stopFlag.get && sortedEntries.hasNext){
          val entry = sortedEntries.next
          if(entry.isDirectory){
            visitor.visitDir(new DetailsImpl(entry, zip, stopFlag))
          }else{
            visitor.visitFile(new DetailsImpl(entry, zip, stopFlag))
          }
        }
      }finally{
        zip.close()
      }
    }catch{
      case e: Exception =>
        throw new GradleException("Could not expand %s.".format(getDisplayName), e)
    }
  }

  private class DetailsImpl(val entry: ZipEntry, val zip: ZipFile, val stopFlag: AtomicBoolean) extends AbstractFileTreeElement(null) with FileVisitDetails {

    private var file: File = null

    def getDisplayName = "zip entry %s!%s".format(zipFile, entry.getName)

    def stopVisiting() = stopFlag.set(true)

    /**
     * Changed this to return a broken value! Be warned! Will not be a valid file, do not read it.
     * Standard Jar/Zip tasks don't care about this, even though they call it.
     */
    def getFile: File = {
      if(file == null){
        file = new File(entry.getName)
      }
      file
    }

    def getLastModified = entry.getTime
    def isDirectory = entry.isDirectory
    def getSize = entry.getSize
    def open: InputStream = {
      try{
        zip.getInputStream(entry)
      }catch{
        case e: IOException => throw new UncheckedIOException(e)
      }
    }

    def getRelativePath = new RelativePath(!entry.isDirectory, entry.getName.split("/"): _*)
  }
}
