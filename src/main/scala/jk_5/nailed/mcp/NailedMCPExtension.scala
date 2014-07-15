package jk_5.nailed.mcp

import org.gradle.api.Project

import scala.collection.mutable

/**
 * No description given
 *
 * @author jk-5
 */
class NailedMCPExtension(val project: Project) {

  private var minecraftVersion: String = _
  private var mainClass: String = _
  private val extraSrg = mutable.ArrayBuffer[String]()

  def setMinecraftVersion(minecraftVersion: String) = this.minecraftVersion = minecraftVersion
  def getMinecraftVersion = this.minecraftVersion
  def setMainClass(mainClass: String) = this.mainClass = mainClass
  def getMainClass = this.mainClass
  def addSrg(srg: String): Unit = this.extraSrg += srg
  def getExtraSrg = this.extraSrg
}
