package jk_5.nailed.mcp.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.{InputFile, TaskAction, OutputFile}
import jk_5.nailed.mcp.delayed.{DelayedString, DelayedFile}
import com.google.gson.{JsonPrimitive, JsonArray, JsonParser, JsonObject}
import java.io.{FileWriter, FileReader}
import jk_5.nailed.mcp.Constants
import jk_5.nailed.mcp.json.JsonFactory
import scala.collection.mutable
import scala.collection.convert.wrapAsScala._
import org.gradle.api.artifacts.Configuration

/**
 * No description given
 *
 * @author jk-5
 */
class GenerateVersionFileTask extends DefaultTask {

  @InputFile private var infoFile: DelayedFile = _
  @OutputFile private var output: DelayedFile = _
  private val configurations = mutable.ArrayBuffer[Configuration]()

  @TaskAction def doTask(){
    val version = getProject.getVersion.asInstanceOf[String]
    val mcversion = new DelayedString("{MC_VERSION}", getProject)
    val p = version.split("-", 2)(0).split("\\.")
    val versionObj = new JsonObject
    versionObj.addProperty("major", p(0).toInt)
    versionObj.addProperty("minor", p(1).toInt)
    versionObj.addProperty("revision", p(2).toInt)
    versionObj.addProperty("full", version)
    versionObj.addProperty("isSnapshot", version.contains("SNAPSHOT"))

    val json = new JsonObject
    json.add("version", versionObj)
    json.addProperty("mcversion", mcversion.call())

    val reader = new FileReader(getInfoFile)
    val obj = new JsonParser().parse(reader).getAsJsonObject
    reader.close()

    val deps = new JsonArray
    val depSet = mutable.HashSet[String]()
    for(cfg <- configurations){
      for(dep <- cfg.getAllDependencies){
        depSet.add(dep.getGroup + ":" + dep.getName + ":" + dep.getVersion)
      }
    }
    depSet.foreach(d => deps.add(new JsonPrimitive(d)))

    json.add("dependencies", deps)

    val repoArray = new JsonArray
    repoArray.add(new JsonPrimitive("http://maven.reening.nl"))
    repoArray.add(new JsonPrimitive("http://files.minecraftforge.net/maven"))
    repoArray.add(new JsonPrimitive(Constants.MINECRAFT_MAVEN_URL))
    repoArray.add(new JsonPrimitive("http://repo1.maven.org/maven2/"))

    json.add("repositories", repoArray)

    val writer = new FileWriter(getOutput)
    JsonFactory.gson.toJson(json, writer)
    writer.close()
  }

  def addConfiguration(configuration: Configuration) = configurations += configuration
  def setOutput(output: DelayedFile) = this.output = output
  def setInfoFile(infoFile: DelayedFile) = this.infoFile = infoFile
  def getOutput = this.output.call()
  def getInfoFile = this.infoFile.call()
}
