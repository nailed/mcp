package jk_5.nailed.mcp

import com.google.common.io.ByteStreams
import jk_5.nailed.mcp.patching.FernFlowerPatcher
import org.junit.{Assert, Test}

/**
 * No description given
 *
 * @author jk-5
 */
class EnumFixerTest {

  @Test def testClass1() = check("enumfixer/Input", "enumfixer/Output")
  @Test def testClass2() = check("enumfixer/Input2", "enumfixer/Output2")

  def check(in: String, out: String){
    val input = FernFlowerPatcher.processFile("Input.java", readResource(in), fixInterfaces = true)
    val expected = readResource(out).split("\r\n|\r|\n")
    val actual = input.split("\r\n|\r|\n")

    Assert.assertEquals(expected.length, actual.length)
    for(i <- 0 until expected.length){
      println("EXPECTED >>" + expected(i))
      println("ACTUAL   >>" + actual(i))
      Assert.assertEquals(expected(i), actual(i))
    }
  }

  @inline def readResource(resource: String) = new String(ByteStreams.toByteArray(this.getClass.getClassLoader.getResourceAsStream(resource)))
}
