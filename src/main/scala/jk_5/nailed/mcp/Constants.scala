package jk_5.nailed.mcp

import java.io.OutputStream

import groovy.lang.Closure

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
  final val MINECRAFT_CONFIGURATION = "minecraft"
  final val NAILED_CONFIGURATION = "nailed"
  final val API_SUBPROJECT = "api"

  final val JAR_UNSHADED = "{CACHE_DIR}/nailed-mcp/net/minecraft/minecraft_unshaded/{MC_VERSION}/minecraft_unshaded-{MC_VERSION}.jar"
  final val JAR_SRG = "{CACHE_DIR}/nailed-mcp/net/minecraft/minecraft_srg/{MC_VERSION}/minecraft_srg-{MC_VERSION}.jar"
  final val DEOBF_DATA = "{CACHE_DIR}/nailed-mcp/net/minecraft/minecraft_srg/{MC_VERSION}/deobfuscation_data-{MC_VERSION}.lzma"
  final val ZIP_DECOMP = "{CACHE_DIR}/nailed-mcp/net/minecraft/minecraft_decomp/{MC_VERSION}/minecraft_decomp-{MC_VERSION}.zip"
  final val REMAPPED_CLEAN = "{BUILD_DIR}/tmp/clean.jar"
  final val ZIP_PATCHED = "{BUILD_DIR}/tmp/dirtyPatched.zip"
  final val ZIP_REMAPPED_DIRTY = "{BUILD_DIR}/tmp/dirtyRemapped.zip"
  final val RUNTIME_DIR = "{PROJECT_DIR}/runtime"
  final val RUNTIME_VERSIONFILE = "{BUILD_DIR}/tmp/nailedversion.json"
  final val RANGEMAP = "{BUILD_DIR}/tmp/rangemap.txt"
  final val PATCH_DIRTY = "{BUILD_DIR}/tmp/dirty-patch-base.zip"
  final val REOBF_TMP = "{BUILD_DIR}/tmp/recompiledObfuscated.jar"
  final val BINPATCH_TMP = "{BUILD_DIR}/tmp/binpatches.jar"

  //Mappings
  final val JOINED_SRG = "{MAPPINGS_DIR}/joined.srg"
  final val JOINED_EXC = "{MAPPINGS_DIR}/joined.exc"
  final val METHODS_CSV = "{MAPPINGS_DIR}/methods.csv"
  final val FIELDS_CSV = "{MAPPINGS_DIR}/fields.csv"
  final val PARAMS_CSV = "{MAPPINGS_DIR}/params.csv"
  final val PACKAGES_CSV = "{MAPPINGS_DIR}/packages.csv"
  final val EXC_JSON = "{MAPPINGS_DIR}/exceptor.json"
  final val MCP_PATCHES = "{MAPPINGS_DIR}/patches"
  final val SHADEDLIB_REMOVE_CONFIG = "{MAPPINGS_DIR}/removeClasses.cfg"
  final val ASTYLE_CONFIG = "{MAPPINGS_DIR}/astyle.cfg"
  final val VERSION_INFO = "{MAPPINGS_DIR}/version.json"

  //Generated files
  final val NOTCH_2_SRG_SRG = "{BUILD_DIR}/tmp/mappings/notch2srg.srg"
  final val NOTCH_2_MCP_SRG = "{BUILD_DIR}/tmp/mappings/notch2mcp.srg"
  final val MCP_2_SRG_SRG = "{BUILD_DIR}/tmp/mappings/mcp2srg.srg"
  final val MCP_2_NOTCH_SRG = "{BUILD_DIR}/tmp/mappings/mcp2notch.srg"
  final val SRG_EXC = "{BUILD_DIR}/tmp/mappings/srg.exc"
  final val MCP_EXC = "{BUILD_DIR}/tmp/mappings/mcp.exc"

  final val NAILED_JAVA_SOURCES = "{PROJECT_DIR}/src/main/java"
  final val NAILED_SCALA_SOURCES = "{PROJECT_DIR}/src/main/scala"
  final val NAILED_RESOURCES = "{PROJECT_DIR}/src/main/resources"
  final val NAILED_JAVA_API_SOURCES = "{PROJECT_DIR}/api/src/main/java"
  final val NAILED_SCALA_API_SOURCES = "{PROJECT_DIR}/api/src/main/scala"
  final val NAILED_API_RESOURCES = "{PROJECT_DIR}/api/src/main/resources"
  final val NAILED_JAVA_TEST_SOURCES = "{PROJECT_DIR}/src/test/java"
  final val NAILED_SCALA_TEST_SOURCES = "{PROJECT_DIR}/src/test/scala"
  final val NAILED_TEST_RESOURCES = "{PROJECT_DIR}/src/test/scala"
  final val PROJECT_CLEAN = "{PROJECT_DIR}/minecraft/Clean"
  final val PROJECT_DIRTY = "{PROJECT_DIR}/minecraft/Nailed"
  final val MINECRAFT_CLEAN_SOURCES = PROJECT_CLEAN + "/src/main/java"
  final val MINECRAFT_CLEAN_RESOURCES = PROJECT_CLEAN + "/src/main/resources"
  final val MINECRAFT_DIRTY_SOURCES = PROJECT_DIRTY + "/src/main/java"
  final val MINECRAFT_DIRTY_RESOURCES = PROJECT_DIRTY + "/src/main/resources"
  final val NAILED_PATCH_DIR = "{PROJECT_DIR}/patches"

  final val MINECRAFT_MAVEN_URL = "https://libraries.minecraft.net"

  final val CALL_FALSE = new Closure[Boolean](null){
    override def call(arguments: scala.Any): Boolean = false
  }

  def getNullStream = new OutputStream{
    override def write(b: Int){}
  }
}
