package jk_5.nailed.mcp.io

import java.util
import net.minecraftforge.srg2source.util.io.InputSupplier
import java.io.InputStream
import scala.collection.convert.wrapAsScala._
import com.google.common.collect.Lists

/**
 * No description given
 *
 * @author jk-5
 */
class SequencedInputSupplier extends util.LinkedList[InputSupplier] with InputSupplier {

  override def getRoot(resource: String): String = {
    for(sup <- this){
      val out = sup.getRoot(resource)
      if(out != null) return out
    }
    null
  }

  override def getInput(relPath: String): InputStream = {
    for(sup <- this){
      val in = sup.getInput(relPath)
      if(in != null) return in
    }
    null
  }

  override def gatherAll(endFilter: String): util.List[String] = {
    val ret = Lists.newLinkedList[String]()
    this.foreach(s => ret.addAll(s.gatherAll(endFilter)))
    ret
  }

  override def close() = this.foreach(_.close)
}
