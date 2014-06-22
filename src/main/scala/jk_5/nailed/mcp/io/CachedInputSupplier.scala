package jk_5.nailed.mcp.io

import net.minecraftforge.srg2source.util.io.InputSupplier
import java.util
import java.io.{File, ByteArrayInputStream, InputStream}
import scala.collection.mutable
import com.google.common.collect.Lists

/**
 * No description given
 *
 * @author jk-5
 */
class CachedInputSupplier extends InputSupplier {

  private val fileMap = mutable.HashMap[String, Array[Byte]]()
  private val rootMap = mutable.HashMap[String, String]()

  override def getRoot(resource: String): String = rootMap.get(sanitize(resource)).getOrElse(null)

  override def getInput(relPath: String): InputStream = new ByteArrayInputStream(fileMap.get(sanitize(relPath)).getOrElse(null))

  override def gatherAll(endFilter: String): util.List[String] = {
    val ret = Lists.newLinkedList[String]()
    fileMap.keySet.filter(_.endsWith(endFilter)).foreach(ret.add)
    ret
  }

  override def close() = {}

  def sanitize(input: String): String = {
    if(input == null) return null
    val in = input.replace('\\', '/')
    if(in.endsWith("/")) in.substring(0, in.length() - 1) else in
  }

  def isEmpty = fileMap.isEmpty && rootMap.isEmpty

  def addFile(path: String, root: File, data: Array[Byte]){
    val p = sanitize(path)
    fileMap.put(p, data)
    rootMap.put(p, sanitize(root.getCanonicalPath))
  }
}
