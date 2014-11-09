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
  private var mappingsAreSet = false
  private var mappingsChannel: String = _
  private var mappingsVersion: String = _

  def setMinecraftVersion(minecraftVersion: String) = this.minecraftVersion = minecraftVersion
  def getMinecraftVersion = this.minecraftVersion
  def setMainClass(mainClass: String) = this.mainClass = mainClass
  def getMainClass = this.mainClass
  def addSrg(srg: String): Unit = this.extraSrg += srg
  def getExtraSrg = this.extraSrg

  def getMappings = mappingsChannel + "_" + mappingsVersion
  def getMappingsChannel = mappingsChannel
  def getMappingsVersion = mappingsVersion
  def mappingsSet = mappingsAreSet

  def setMappings(mappings: String){
    if(mappings == null || mappings.isEmpty){
      mappingsVersion = null
      mappingsChannel = null
      mappingsAreSet = false
      return
    }

    if(!mappings.contains("_")){
      throw new IllegalArgumentException("Mappings must be in format 'channel_version'. eg: snapshot_20141109")
    }

    val index = mappings.lastIndexOf('_')
    mappingsChannel = mappings.substring(0, index)
    mappingsVersion = mappings.substring(index + 1)
    mappingsAreSet = true
  }
}
