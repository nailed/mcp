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

  final val FERNFLOWER_CONFIGURATION = "fernFlower"
  final val MINECRAFT_CONFIGURATION = "minecraft"
  final val NAILED_CONFIGURATION = "nailed"
  final val MCJAR_CONFIGURATION = "mcjar"
  final val MAPPINGS_CONFIGURATION = "mappings"
  final val API_SUBPROJECT = "api"

  final val JAR_UNSHADED = "{BUILD_DIR}/tmp/jars/minecraft-unshaded.jar"
  final val JAR_SRG = "{BUILD_DIR}/tmp/jars/minecraft-remapped.jar"
  final val DEOBF_DATA = "{BUILD_DIR}/tmp/data/deobfuscation_data.lzma"
  final val ZIP_DECOMP = "{BUILD_DIR}/tmp/jars/minecraft-decompiled.zip"
  final val PATCHED_DIRTY = "{BUILD_DIR}/tmp/jars/dirty-patched.zip"
  final val REMAPPED_CLEAN = "{BUILD_DIR}/tmp/jars/clean-remapped.jar"
  final val REMAPPED_DIRTY = "{BUILD_DIR}/tmp/jars/dirty-remapped.jar"
  final val RUNTIME_DIR = "{PROJECT_DIR}/runtime"
  final val RUNTIME_VERSIONFILE = "{BUILD_DIR}/tmp/data/nailedversion.json"
  final val RANGEMAP = "{BUILD_DIR}/tmp/data/rangemap.txt"
  final val DIRTY_REMAPPED_SRC = "{BUILD_DIR}/tmp/jars/patch-dirty.zip"
  final val REOBFUSCATED = "{BUILD_DIR}/tmp/jars/reobfuscated.jar"
  final val BINPATCHES = "{BUILD_DIR}/tmp/jars/binpatches.jar"

  //Mappings
  final val JOINED_SRG = "{MAPPINGS_DIR}/joined.srg"
  final val JOINED_EXC = "{MAPPINGS_DIR}/joined.exc"
  final val EXC_JSON = "{MAPPINGS_DIR}/exceptor.json"
  final val MCP_PATCHES = "{MAPPINGS_DIR}/patches"
  final val SHADEDLIB_REMOVE_CONFIG = "{MAPPINGS_DIR}/removeClasses.cfg"
  final val ASTYLE_CONFIG = "{MAPPINGS_DIR}/astyle.cfg"
  final val VERSION_INFO = "{MAPPINGS_DIR}/version.json"

  //Generated files
  final val NOTCH_2_SRG_SRG = "{BUILD_DIR}/tmp/mappings/generated/srg/notch2srg.srg"
  final val NOTCH_2_MCP_SRG = "{BUILD_DIR}/tmp/mappings/generated/srg/notch2mcp.srg"
  final val MCP_2_SRG_SRG = "{BUILD_DIR}/tmp/mappings/generated/srg/mcp2srg.srg"
  final val MCP_2_NOTCH_SRG = "{BUILD_DIR}/tmp/mappings/generated/srg/mcp2notch.srg"
  final val SRG_EXC = "{BUILD_DIR}/tmp/mappings/generated/exc/srg.exc"
  final val MCP_EXC = "{BUILD_DIR}/tmp/mappings/generated/exc/mcp.exc"
  final val METHODS_CSV = "{BUILD_DIR}/tmp/mappings/csv/methods.csv"
  final val FIELDS_CSV = "{BUILD_DIR}/tmp/mappings/csv/fields.csv"
  final val PARAMS_CSV = "{BUILD_DIR}/tmp/mappings/csv/params.csv"
  final val CSV_MAPPINGS_DIR = "{BUILD_DIR}/tmp/mappings/csv/"
  final val STATICS_LIST = "{BUILD_DIR}/tmp/mappings/generated/statics.txt"

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
