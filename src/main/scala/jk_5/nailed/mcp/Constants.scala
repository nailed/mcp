package jk_5.nailed.mcp

import scala.util.Properties

/**
 * No description given
 *
 * @author jk-5
 */
object Constants {

  final val MCP_EXTENSION_NAME = "nailedMCP"
  final val NEWLINE = Properties.lineSeparator
  final val HASH_FUNC = "MD5"

  final val SERVER_JAR_VANILLA = "{CACHE_DIR}/nailed-mcp/net/minecraft/minecraft_server/{MC_VERSION}/minecraft_server-{MC_VERSION}.jar"
  final val MC_SERVER_URL = "http://s3.amazonaws.com/Minecraft.Download/versions/{MC_VERSION}/minecraft_server.{MC_VERSION}.jar"

  final val FERNFLOWER_CONFIGURATION = "fernFlower"

  //Mappings
  final val JOINED_SRG = "{MAPPINGS_DIR}/joined.srg"
  final val JOINED_EXC = "{MAPPINGS_DIR}/joined.exc"
  final val METHODS_CSV = "{MAPPINGS_DIR}/methods.csv"
  final val FIELDS_CSV = "{MAPPINGS_DIR}/fields.csv"
  final val PARAMS_CSV = "{MAPPINGS_DIR}/params.csv"
  final val PACKAGES_CSV = "{MAPPINGS_DIR}/packages.csv"

  //Generated files
  final val NOTCH_2_SRG_SRG = "{BUILD_DIR}/tmp/mappings/notch2srg.srg"
  final val NOTCH_2_MCP_SRG = "{BUILD_DIR}/tmp/mappings/notch2mcp.srg"
  final val MCP_2_SRG_SRG = "{BUILD_DIR}/tmp/mappings/mcp2srg.srg"
  final val MCP_2_NOTCH_SRG = "{BUILD_DIR}/tmp/mappings/mcp2notch.srg"
  final val SRG_EXC = "{BUILD_DIR}/tmp/mappings/srg.exc"
  final val MCP_EXC = "{BUILD_DIR}/tmp/mappings/mcp.exc"
}
