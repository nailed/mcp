package jk_5.nailed.mcp

import org.gradle.api.Project

/**
 * No description given
 *
 * @author jk-5
 */
class NailedMCPExtension(val project: Project) {

  private var minecraftVersion: String = _

  def setMinecraftVersion(minecraftVersion: String) = this.minecraftVersion = minecraftVersion
  def getMinecraftVersion = this.minecraftVersion
}
