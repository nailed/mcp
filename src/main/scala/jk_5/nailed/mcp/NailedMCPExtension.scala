package jk_5.nailed.mcp

import org.gradle.api.Project

/**
 * No description given
 *
 * @author jk-5
 */
class NailedMCPExtension(val project: Project) {

  private var minecraftVersion: String = _
  private var mainClass: String = _

  def setMinecraftVersion(minecraftVersion: String) = this.minecraftVersion = minecraftVersion
  def getMinecraftVersion = this.minecraftVersion
  def setMainClass(mainClass: String) = this.mainClass = mainClass
  def getMainClass = this.mainClass
}
