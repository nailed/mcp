package jk_5.nailed.mcp

import com.google.common.io.ByteStreams
import jk_5.nailed.mcp.patching.ClassNameCleanup
import org.junit.{Assert, Test}

/**
 * No description given
 *
 * @author jk-5
 */
class ClassNameCleanupTest {

  @Test def testClass1() = check("classnamecleanup/Input", "classnamecleanup/Output")
  @Test def testClass2() = check("classnamecleanup/Input2", "classnamecleanup/Output2")
  @Test def testClass3() = check("classnamecleanup/Input3", "classnamecleanup/Output3")
  @Test def testClass4() = check("classnamecleanup/Input4", "classnamecleanup/Output4")

  def check(in: String, out: String){
    val input = ClassNameCleanup.renameClass(readResource(in))
    val expected = readResource(out).split("\r\n|\r|\n")
    val actual = input.split("\r\n|\r|\n")

    /*val f = new File(s"out${ClassNameCleanupTest.num}.txt")
    if(f.isFile) f.delete()
    val writer = new FileWriter(f)
    writer.write(input)
    writer.close()
    ClassNameCleanupTest.num += 1*/

    Assert.assertEquals(expected.length, actual.length)
    for(i <- 0 until expected.length){
      println("EXPECTED >>" + expected(i))
      println("ACTUAL   >>" + actual(i))
      Assert.assertEquals(expected(i), actual(i))
    }
  }

  @inline def readResource(resource: String) = new String(ByteStreams.toByteArray(this.getClass.getClassLoader.getResourceAsStream(resource)))
}
