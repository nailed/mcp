package jk_5.nailed.mcp.tasks

import org.gradle.api.DefaultTask
import jk_5.nailed.mcp.delayed.DelayedFile
import org.gradle.api.tasks.{TaskAction, InputFile}
import scala.collection.mutable
import scala.collection.convert.wrapAsScala._
import java.io.{FileReader, File}
import groovy.lang.Closure
import com.google.gson.JsonParser
import com.google.common.io.Files
import jk_5.nailed.mcp.Constants
import com.google.common.base.Charsets
import java.net.URI

/**
 * No description given
 *
 * @author jk-5
 */
class GenerateProjectTask extends DefaultTask {

  private var targetDir: DelayedFile = _
  @InputFile private var versionInfo: DelayedFile = _

  private val javaSources = mutable.ArrayBuffer[DelayedFile]()
  private val scalaSources = mutable.ArrayBuffer[DelayedFile]()
  private val resources = mutable.ArrayBuffer[DelayedFile]()
  private val javaTestSources = mutable.ArrayBuffer[DelayedFile]()
  private val scalaTestSources = mutable.ArrayBuffer[DelayedFile]()
  private val testResources = mutable.ArrayBuffer[DelayedFile]()
  private val dependencies = mutable.ArrayBuffer[String]()

  private val builder = new StringBuilder

  this.getOutputs.file(this.getTargetFile)

  @TaskAction def doTask(){
    val reader = new FileReader(this.getVersionInfo)
    val json = new JsonParser().parse(reader).getAsJsonObject
    reader.close()
    json.getAsJsonArray("dependencies").foreach(d => this.dependencies += d.getAsString)

    val file = getProject.file(this.getTargetFile.call())
    file.getParentFile.mkdirs()
    Files.touch(file)

    val base = this.targetDir.call().toURI

    write("apply plugin: 'java'")
    write("apply plugin: 'idea'")
    write("")
    write("sourceCompatibility = '1.6'")
    write("targetCompatibility = '1.6'")
    write("")
    write("repositories {")
    write("    mavenCentral()")
    write("    maven {")
    write("        name 'reening'")
    write("        url 'http://maven.reening.nl/'")
    write("    }")
    write("    maven {")
    write("        name 'forge'")
    write("        url 'http://files.minecraftforge.net/maven/'")
    write("    }")
    write("    maven {")
    write("        name 'minecraft'")
    write(s"        url '${Constants.MINECRAFT_MAVEN_URL}'")
    write("    }")
    write("}")
    write("")
    write("dependencies {")
    this.dependencies.foreach(d => write(s"    compile '$d'"))
    write("    ")
    write("    testCompile 'junit:junit:4.11'")
    write("}")
    if(javaSources.size + scalaSources.size + resources.size + javaTestSources.size + scalaTestSources.size + testResources.size > 0){
      write("")
      write("sourceSets {")
      if(javaSources.size + scalaSources.size + resources.size > 0){
        write("    main {")
        if(javaSources.size > 0){
          write("        java {")
          javaSources.foreach(s => write(s"            srcDir '${relative(base, s)}'"))
          write("        }")
        }
        if(scalaSources.size > 0){
          write("        scala {")
          javaSources.foreach(s => write(s"            srcDir '${relative(base, s)}'"))
          write("        }")
        }
        if(resources.size > 0){
          write("        resources {")
          resources.foreach(s => write(s"            srcDir '${relative(base, s)}'"))
          write("        }")
        }
        write("    }")
      }
      if(javaTestSources.size + scalaTestSources.size + resources.size > 0){
        write("    test {")
        if(javaTestSources.size > 0){
          write("        java {")
          javaTestSources.foreach(s => write(s"            srcDir '${relative(base, s)}'"))
          write("        }")
        }
        if(scalaTestSources.size > 0){
          write("        scala {")
          scalaTestSources.foreach(s => write(s"            srcDir '${relative(base, s)}'"))
          write("        }")
        }
        if(testResources.size > 0){
          write("        resources {")
          testResources.foreach(s => write(s"            srcDir '${relative(base, s)}'"))
          write("        }")
        }
        write("    }")
      }
      write("}")
    }
    Files.write(this.builder.toString(), file, Charsets.UTF_8)
  }

  private def relative(base: URI, src: DelayedFile): String = {
    var relative = base.relativize(src.call().toURI).getPath.replace('\\', '/')
    if(!relative.endsWith("/")) relative += "/"
    relative
  }

  private def write(lines: String*) = lines.foreach(l => builder.append(l).append(Constants.NEWLINE))

  def addJavaSource(source: DelayedFile) = this.javaSources += source
  def addScalaSource(source: DelayedFile) = this.scalaSources += source
  def addResource(source: DelayedFile) = this.resources += source
  def addJavaTestSource(source: DelayedFile) = this.javaTestSources += source
  def addScalaTestSource(source: DelayedFile) = this.scalaTestSources += source
  def addTestResource(source: DelayedFile) = this.testResources += source

  def setTargetDir(targetDir: DelayedFile) = this.targetDir = targetDir
  def setVersionInfo(versionInfo: DelayedFile) = this.versionInfo = versionInfo
  def getTargetDir = this.targetDir.call()
  def getVersionInfo = this.versionInfo.call()
  def getTargetFile = new Closure[File](this){
    override def call() = new File(getTargetDir, "build.gradle")
    override def call(arguments: scala.Any) = new File(getTargetDir, "build.gradle")
  }
}
