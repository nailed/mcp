package jk_5.nailed.mcp

import _root_.java.io.FileReader
import org.gradle.api._
import _root_.java.util
import jk_5.nailed.mcp.delayed.{DelayedFile, DelayedString}
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import jk_5.nailed.mcp.tasks._
import scala.collection.convert.wrapAsScala._
import org.gradle.api.tasks.Copy
import com.google.gson.JsonParser
import org.gradle.plugins.ide.idea.model.IdeaModel
import groovy.lang.Closure
import org.w3c.dom.{Element, Document}
import org.gradle.api.plugins.JavaPluginConvention

/**
 * No description given
 *
 * @author jk-5
 */
class McpPlugin extends Plugin[Project] {

  var project: Project = _

  final val javaFiles = Array("**.java", "*.java", "**/*.java")

  override def apply(project: Project){
    this.project = project

    project.afterEvaluate(new Action[Project] {
      override def execute(project: Project){
        afterEvaluate(project)
      }
    })

    project.allprojects(new Action[Project] {
      override def execute(project: Project){
        project.getRepositories.maven(new Action[MavenArtifactRepository]{
          override def execute(repo: MavenArtifactRepository){
            repo.setName("minecraft")
            repo.setUrl(Constants.MINECRAFT_MAVEN_URL)
          }
        })
        project.getRepositories.mavenCentral
        project.getRepositories.maven(new Action[MavenArtifactRepository]{
          override def execute(repo: MavenArtifactRepository){
            repo.setName("reening")
            repo.setUrl("http://maven.reening.nl")
          }
        })
        project.getRepositories.maven(new Action[MavenArtifactRepository]{
          override def execute(repo: MavenArtifactRepository){
            repo.setName("forge")
            repo.setUrl("http://files.minecraftforge.net/maven")
          }
        })
      }
    })

    project.getExtensions.create(Constants.MCP_EXTENSION_NAME, classOf[NailedMCPExtension], project)

    project.getConfigurations.create(Constants.FERNFLOWER_CONFIGURATION)
    val mcCfg = project.getConfigurations.create(Constants.MINECRAFT_CONFIGURATION)
    val nailedCfg = project.getConfigurations.create(Constants.NAILED_CONFIGURATION)
    project.getConfigurations.getByName("compile").extendsFrom(mcCfg)
    project.getConfigurations.getByName("compile").extendsFrom(nailedCfg)
    project.getDependencies.add(Constants.FERNFLOWER_CONFIGURATION, "de.fernflower:fernflower:1.0")

    makeTask("downloadServer", classOf[DownloadTask]){ t =>
      t.setOutput(Constants.SERVER_JAR_VANILLA)
      t.setUrl(Constants.MC_SERVER_URL)
    }

    makeTask("removeShadedLibs", classOf[RemoveShadedLibsTask]){ t =>
      t.setConfig(Constants.SHADEDLIB_REMOVE_CONFIG)
      t.setInJar(Constants.SERVER_JAR_VANILLA)
      t.setOutJar(Constants.JAR_UNSHADED)
      t.dependsOn("downloadServer")
    }

    makeTask("generateMappings", classOf[GenerateMappingsTask]){ t =>
      t.setInSrg(Constants.JOINED_SRG)
      t.setInExc(Constants.JOINED_EXC)
      t.setMethodCsv(Constants.METHODS_CSV)
      t.setFieldCsv(Constants.FIELDS_CSV)
      t.setNotchToSrg(Constants.NOTCH_2_SRG_SRG)
      t.setNotchToMcp(Constants.NOTCH_2_MCP_SRG)
      t.setMcpToSrg(Constants.MCP_2_SRG_SRG)
      t.setMcpToNotch(Constants.MCP_2_NOTCH_SRG)
      t.setSrgExc(Constants.SRG_EXC)
      t.setMcpExc(Constants.MCP_EXC)
      t.setDoesCache(false)

      for(f <- project.fileTree(toDelayedFile(Constants.NAILED_RESOURCES).call()).getFiles){
        if(f.getPath.endsWith(".exc")){
          t.addExtraExc(f)
        }else if(f.getPath.endsWith(".srg")){
          t.addExtraSrg(f)
        }
      }
    }

    makeTask("deobfuscate", classOf[DeobfuscateTask]){ t =>
      t.setInJar(Constants.JAR_UNSHADED)
      t.setOutJar(Constants.JAR_SRG)
      t.setSrg(Constants.NOTCH_2_SRG_SRG)
      t.setExceptorConfig(Constants.JOINED_EXC)
      t.setExceptorJson(Constants.EXC_JSON)
      t.setApplyMarkers(applyMarkers = true)
      t.setFieldCsv(Constants.FIELDS_CSV)
      t.setMethodCsv(Constants.METHODS_CSV)
      t.dependsOn("removeShadedLibs", "generateMappings")

      for(f <- project.fileTree(toDelayedFile(Constants.NAILED_RESOURCES).call()).getFiles){
        if(f.getPath.endsWith("_at.cfg")){
          t.addAccessTransformer(f)
        }
      }
    }

    makeTask("decompile", classOf[DecompileTask]){ t =>
      t.setInJar(Constants.JAR_SRG)
      t.setFernFlowerJar(project.getConfigurations.getByName(Constants.FERNFLOWER_CONFIGURATION).getSingleFile)
      t.setPatch(Constants.MCP_PATCHES)
      t.setOutJar(Constants.ZIP_DECOMP)
      t.setAStyleConfig(Constants.ASTYLE_CONFIG)
      t.dependsOn("deobfuscate", "generateMappings")
    }

    makeTask("remapCleanSource", classOf[RemapSourceTask]){ t =>
      t.setInJar(Constants.ZIP_DECOMP)
      t.setOutJar(Constants.REMAPPED_CLEAN)
      t.setMethodCsv(Constants.METHODS_CSV)
      t.setFieldCsv(Constants.FIELDS_CSV)
      t.setParamCsv(Constants.PARAMS_CSV)
      t.setDoesCache(false)
      t.noJavadocs()
      t.dependsOn("decompile")
    }

    makeTask("patchDirtySource", classOf[PatchSourceJarTask]){ t =>
      t.setInJar(Constants.ZIP_DECOMP)
      t.setOutJar(Constants.ZIP_PATCHED)
      t.addStage("Nailed", Constants.NAILED_PATCH_DIR)
      t.setDoesCache(false)
      t.setMaxFuzz(2)
      t.dependsOn("decompile")
    }

    makeTask("remapDirtySource", classOf[RemapSourceTask]){ t =>
      t.setInJar(Constants.ZIP_PATCHED)
      t.setOutJar(Constants.ZIP_REMAPPED_DIRTY)
      t.setMethodCsv(Constants.METHODS_CSV)
      t.setFieldCsv(Constants.FIELDS_CSV)
      t.setParamCsv(Constants.PARAMS_CSV)
      t.setDoesCache(false)
      t.noJavadocs()
      t.dependsOn("patchDirtySource")
    }

    makeTask("extractMinecraftResources", classOf[ExtractTask]){ t =>
      t.exclude(this.javaFiles: _*)
      t.setIncludeEmptyDirs(includeEmptyDirs = false)
      t.from(Constants.REMAPPED_CLEAN)
      t.into(Constants.MINECRAFT_CLEAN_RESOURCES)
      t.dependsOn("remapCleanSource" /*, "extractWorkspace"*/)
    }

    makeTask("extractMinecraftSources", classOf[ExtractTask]){ t =>
      t.include(this.javaFiles: _*)
      t.from(Constants.REMAPPED_CLEAN)
      t.into(Constants.MINECRAFT_CLEAN_SOURCES)
      t.dependsOn("extractMinecraftResources")
    }

    makeTask("extractNailedResources", classOf[ExtractTask]){ t =>
      t.exclude(this.javaFiles: _*)
      t.setIncludeEmptyDirs(includeEmptyDirs = false)
      t.from(Constants.ZIP_REMAPPED_DIRTY)
      t.into(Constants.MINECRAFT_DIRTY_RESOURCES)
      t.dependsOn("remapDirtySource")
    }

    makeTask("compressDeobfData", classOf[CompressLzmaTask]){ t =>
      t.setInput(Constants.NOTCH_2_SRG_SRG)
      t.setOutput(Constants.DEOBF_DATA)
      t.dependsOn("generateMappings")
    }

    makeTask("copyDeobfData", classOf[Copy]){ t =>
      t.from(Constants.DEOBF_DATA)
      t.from(Constants.RUNTIME_VERSIONFILE)
      t.into(Constants.MINECRAFT_DIRTY_RESOURCES)
      t.dependsOn("extractNailedResources", "compressDeobfData", "generateVersionFile")
    }

    makeTask("extractNailedSources", classOf[ExtractTask]){ t =>
      t.include(this.javaFiles: _*)
      t.from(Constants.ZIP_REMAPPED_DIRTY)
      t.into(Constants.MINECRAFT_DIRTY_SOURCES)
      t.dependsOn("copyDeobfData")
    }

    makeTask("generateVersionFile", classOf[GenerateVersionFileTask]){ t =>
      t.setInfoFile(Constants.VERSION_INFO)
      t.setOutput(Constants.RUNTIME_VERSIONFILE)
      t.addConfiguration(mcCfg)
      t.addConfiguration(nailedCfg)
      t.getOutputs.upToDateWhen(Constants.CALL_FALSE)
    }

    makeTask("setupNailed").dependsOn("extractNailedSources", "extractMinecraftSources")

    val ideaConv = project.getExtensions.getByName("idea").asInstanceOf[IdeaModel]
    ideaConv.getModule.getExcludeDirs.addAll(project.files(".gradle", "build", ".idea").getFiles)
    ideaConv.getModule.setDownloadJavadoc(true)
    ideaConv.getModule.setDownloadSources(true)

    if(ideaConv.getWorkspace.getIws != null){
      ideaConv.getWorkspace.getIws.withXml(new Closure[AnyRef](this, null){
        override def call(args: AnyRef*): AnyRef = {
          val root = this.getDelegate.asInstanceOf[XmlProvider].asElement
          val doc = root.getOwnerDocument
          try{
            injectIdeaRunConfigs(doc, project.getProjectDir.getCanonicalPath)
          }catch{
            case e: Exception => e.printStackTrace()
          }
          null
        }
      })
    }

    val javaConv = project.getConvention.getPlugins.get("java").asInstanceOf[JavaPluginConvention]

    val main = javaConv.getSourceSets.getByName("main")
    main.getJava.srcDir(toDelayedFile(Constants.MINECRAFT_DIRTY_SOURCES))
    main.getResources.srcDir(toDelayedFile(Constants.MINECRAFT_DIRTY_RESOURCES))

    javaConv.setSourceCompatibility("1.6")
    javaConv.setTargetCompatibility("1.6")
  }

  def afterEvaluate(project: Project){
    val reader = new FileReader(new DelayedFile(Constants.VERSION_INFO, project).call())
    val json = new JsonParser().parse(reader).getAsJsonObject
    reader.close()

    val deps = project.getDependencies
    for(dep <- json.getAsJsonArray("dependencies")){
      deps.add(Constants.MINECRAFT_CONFIGURATION, dep.getAsString)
    }
  }

  def injectIdeaRunConfigs(doc: Document, module: String){
    val list = doc.getElementsByTagName("component")
    val ext = project.getExtensions.getByType(classOf[NailedMCPExtension])
    var root: Element = null
    for(i <- 0 until list.getLength){
      val e = list.item(i).asInstanceOf[Element]
      if(e.getAttribute("name") == "RunManager"){
        root = e
      }
    }
    {
      val child = add(root, "configuration",
        "default", "false",
        "name", "Run Nailed Server",
        "type", "Application",
        "factoryName", "Application"
      )
      add(child, "option", "name", "MAIN_CLASS_NAME", "value", ext.getMainClass)
      add(child, "option", "name", "VM_PARAMETERS", "value", "-server -Xms512M -Xmx512M -XX:+AggressiveOpts -XX:+OptimizeStringConcat -XX:+UseFastAccessorMethods -XX:+UseParNewGC -XX:+UseConcMarkSweepGC -XX:+CMSParallelRemarkEnabled -Djava.awt.headless=true")
      add(child, "option", "name", "PROGRAM_PARAMETERS", "value", "nogui")
      add(child, "option", "name", "WORKING_DIRECTORY", "value", "file://" + toDelayedFile(Constants.RUNTIME_DIR).call().getCanonicalPath.replace(module, "$PROJECT_DIR$"))
      add(child, "option", "name", "ALTERNATIVE_JRE_PATH_ENABLED", "value", "false")
      add(child, "option", "name", "ALTERNATIVE_JRE_PATH", "value", "")
      add(child, "option", "name", "ENABLE_SWING_INSPECTOR", "value", "false")
      add(child, "option", "name", "ENV_VARIABLES")
      add(child, "option", "name", "PASS_PARENT_ENVS", "value", "true")
      add(child, "module", "name", project.getExtensions.getByName("idea").asInstanceOf[IdeaModel].getModule.getName)
      add(child, "envs")
      add(child, "RunnerSettings", "RunnerId", "Run")
      add(child, "ConfigurationWrapper", "RunnerId", "Run")
      add(child, "method")
    }
    addGradleRunConfig(root, "Update Patches", "generatePatches")
    addGradleRunConfig(root, "Clean Dirty Sources", "cleanDirty")
    addGradleRunConfig(root, "Setup Nailed", "setupNailed")
  }

  def addGradleRunConfig(root: Element, name: String, task: String){
    val child = add(root, "configuration",
      "default", "false",
      "name", name,
      "type", "GradleRunConfiguration",
      "factoryName", "Gradle"
    )
    val s = add(root, "ExternalSystemSettings")
    add(s, "option", "name", "externalProjectPath", "value", "$PROJECT_DIR$/build.gradle")
    add(s, "option", "name", "externalSystemIdString", "value", "GRADLE")
    add(s, "option", "name", "scriptParameters", "value", "")
    val desc = add(s, "option", "name", "taskDescriptions")
    add(desc, "list")
    val names = add(s, "option", "name", "taskNames")
    add(add(names, "list"), "option", "value", task)
    add(s, "option", "name", "vmOptions", "value", "")
    add(child, "method")
  }

  def add(parent: Element, name: String, values: String*): Element = {
    val e = parent.getOwnerDocument.createElement(name)
    var i = 0
    while(i < values.length){
      e.setAttribute(values(i), values(i + 1))
      i += 2
    }
    parent.appendChild(e)
    e
  }

  @inline def makeTask(name: String): DefaultTask = makeTask(this.project, name, classOf[DefaultTask]){t => }
  @inline def makeTask[T <: Task](name: String, cl: Class[T])(configure: (T) => Unit): T = makeTask(this.project, name, cl)(configure)
  def makeTask[T <: Task](project: Project, name: String, cl: Class[T])(configure: (T) => Unit): T = {
    val map = new util.HashMap[String, AnyRef]()
    map.put("name", name)
    map.put("type", cl)
    val t = project.task(map, name).asInstanceOf[T]
    configure(t)
    t
  }

  implicit def toDelayedString(s: String): DelayedString = new DelayedString(s, this.project)
  implicit def toDelayedFile(s: String): DelayedFile = new DelayedFile(s, this.project)
}
