package jk_5.nailed.mcp

import java.io.{File, StringReader}
import java.net.URLClassLoader
import java.nio.charset.Charset
import java.util

import au.com.bytecode.opencsv.{CSVParser, CSVReader}
import com.google.common.collect.{ImmutableList, Lists}
import com.google.common.io.{CharStreams, Files, InputSupplier}

import scala.collection.mutable

/**
 * No description given
 *
 * @author jk-5
 */
object Utils {

  final val UTF_8 = Charset.forName("UTF-8")

  def newCsvReader(file: File) = new CSVReader(Files.newReader(file, UTF_8), CSVParser.DEFAULT_SEPARATOR, CSVParser.DEFAULT_QUOTE_CHARACTER, CSVParser.DEFAULT_ESCAPE_CHARACTER, 1, false)

  def getClassPath: util.List[String] = {
    val urls = this.getClass.getClassLoader.asInstanceOf[URLClassLoader].getURLs
    val ret = Lists.newArrayListWithCapacity[String](urls.length)
    urls.foreach(r => ret.add(r.getPath))
    ret
  }

  def lines(str: String): util.List[String] = try{
    ImmutableList.copyOf(CharStreams.readLines(new InputSupplier[StringReader](){
      override def getInput = new StringReader(str)
    }): java.lang.Iterable[String])
  }catch{
    case e: Exception => ImmutableList.of[String]()
  }

  class DeletableIterator[T](it : Iterator[T]) extends Iterator[T]{
    val deleted = mutable.Set[T]()
    var nextElement: Option[T] = getNext
    private def getNext: Option[T] = {
      if(it.hasNext){
        val n = it.next()
        if(deleted contains n) getNext else Some(n)
      }else None
    }
    def next() = {
      val r = nextElement.get
      nextElement = getNext
      r
    }
    def hasNext = nextElement.isDefined
    def -(x : T) = {
      deleted += x
      this
    }
  }

  implicit def iterator2DeletableIterator[X](i : Iterator[X]) = new DeletableIterator(i)
}
