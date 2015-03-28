package jk_5.nailed.mcp

import _root_.java.io.FileReader
import _root_.java.util

import com.google.common.collect.ImmutableMap
import com.google.gson.JsonParser
import groovy.lang.Closure
import jk_5.nailed.mcp.delayed._
import jk_5.nailed.mcp.tasks._
import org.gradle.api._
import org.gradle.api.artifacts.repositories.{IvyArtifactRepository, MavenArtifactRepository}
import org.gradle.api.file.FileTree
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.bundling.Jar
import org.gradle.plugins.ide.idea.model.IdeaModel
import org.w3c.dom.{Document, Element}

import scala.collection.convert.wrapAsScala._

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
        addMavenRepo(project){ r =>
          r.setName("minecraft")
          r.setUrl(Constants.MINECRAFT_MAVEN_URL)
        }
        project.getRepositories.mavenCentral
        addMavenRepo(project){ r =>
          r.setName("jk-5")
          r.setUrl("http://maven.jk-5.nl")
        }
        addMavenRepo(project){ r =>
          r.setName("forge")
          r.setUrl("http://files.minecraftforge.net/maven")
        }
        addIvyRepo(project){ r =>
          r.setName("Minecraft amazon bucket")
          r.artifactPattern("http://s3.amazonaws.com/Minecraft.Download/versions/[revision]/[module].[revision].jar")
          r.ivyPattern("http://s3.amazonaws.com/file.xml")
        }
      }
    })

    project.getExtensions.create(Constants.MCP_EXTENSION_NAME, classOf[NailedMCPExtension], project)

    project.getConfigurations.create(Constants.FERNFLOWER_CONFIGURATION)
    project.getConfigurations.create(Constants.MCJAR_CONFIGURATION)
    project.getConfigurations.create(Constants.MAPPINGS_CONFIGURATION)
    val mcCfg = project.getConfigurations.create(Constants.MINECRAFT_CONFIGURATION)
    val nailedCfg = project.getConfigurations.create(Constants.NAILED_CONFIGURATION)
    project.getConfigurations.getByName("compile").extendsFrom(mcCfg)
    project.getConfigurations.getByName("compile").extendsFrom(nailedCfg)

    project.getDependencies.add(Constants.FERNFLOWER_CONFIGURATION, "de.fernflower:fernflower:1.0")

    val apiProject = project.getSubprojects.find(_.getName == Constants.API_SUBPROJECT).get

    makeTask[RemoveShadedLibsTask]("removeShadedLibs"){ t =>
      t.setConfig(Constants.SHADEDLIB_REMOVE_CONFIG)
      t.setOutJar(Constants.JAR_UNSHADED)
      t.setDescription("Removes all the shaded libraries and files from the minecraft jar")
    }

    makeTask[ExtractTask]("extractMappings"){ t =>
      t.into(Constants.CSV_MAPPINGS_DIR)
      t.from(new DelayedFile("dummy", this.project) {
        override def resolve() = project.getConfigurations.getByName(Constants.MAPPINGS_CONFIGURATION).getSingleFile
      })
      t.setDoesCache(true);
    }

    makeTask[GenerateMappingsTask]("generateMappings"){ t =>
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
          project.getLogger.lifecycle("Added extra exc file " + f.getName)
          t.addExtraExc(f)
        }else if(f.getPath.endsWith(".srg")){
          project.getLogger.lifecycle("Added extra srg file " + f.getName)
          t.addExtraSrg(f)
        }
      }

      t.dependsOn("extractMappings")
      t.setDescription("Generates remapped .srg and .exc files from the joined srg and exc files combined with the mcp mappings")
    }

    makeTask[DeobfuscateTask]("deobfuscate"){ t =>
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
          project.getLogger.lifecycle("Added AccessTransformer file " + f.getName)
          t.addAccessTransformer(f)
        }
      }
      t.setDescription("Remaps the obfuscated jar to srgnames and applies the exc files")
    }

    makeTask[DecompileTask]("decompile"){ t =>
      t.setInJar(Constants.JAR_SRG)
      t.setPatch(Constants.MCP_PATCHES)
      t.setOutJar(Constants.ZIP_DECOMP)
      t.setAStyleConfig(Constants.ASTYLE_CONFIG)
      t.dependsOn("deobfuscate", "generateMappings")
      t.setDescription("Decompiles the jar")
    }

    makeTask[RemapSourceTask]("remapCleanSource"){ t =>
      t.setInJar(Constants.ZIP_DECOMP)
      t.setOutJar(Constants.REMAPPED_CLEAN)
      t.setMethodCsv(Constants.METHODS_CSV)
      t.setFieldCsv(Constants.FIELDS_CSV)
      t.setParamCsv(Constants.PARAMS_CSV)
      t.setDoesCache(false)
      t.noJavadocs()
      t.dependsOn("decompile")
      t.setDescription("Remaps the clean srg source jar to mapped mcp names")
    }

    makeTask[PatchSourceJarTask]("patchDirtySource"){ t =>
      t.setInJar(Constants.ZIP_DECOMP)
      t.setOutJar(Constants.PATCHED_DIRTY)
      t.addStage("Nailed", Constants.NAILED_PATCH_DIR)
      t.setDoesCache(false)
      t.setMaxFuzz(2)
      t.dependsOn("decompile")
      t.setDescription("Applies the nailed patches to the source jar")
    }

    makeTask[RemapSourceTask]("remapDirtySource"){ t =>
      t.setInJar(Constants.PATCHED_DIRTY)
      t.setOutJar(Constants.REMAPPED_DIRTY)
      t.setMethodCsv(Constants.METHODS_CSV)
      t.setFieldCsv(Constants.FIELDS_CSV)
      t.setParamCsv(Constants.PARAMS_CSV)
      t.setDoesCache(false)
      t.noJavadocs()
      t.dependsOn("patchDirtySource")
      t.setDescription("Remaps the dirty srg source jar to mapped mcp names")
    }

    makeTask[ExtractTask]("extractMinecraftResources"){ t =>
      t.exclude(this.javaFiles: _*)
      t.setIncludeEmptyDirs(includeEmptyDirs = false)
      t.from(Constants.REMAPPED_CLEAN)
      t.into(Constants.MINECRAFT_CLEAN_RESOURCES)
      t.dependsOn("remapCleanSource" /*, "extractWorkspace"*/)
      t.setDescription("Extracts the minecraft resources from the jar into the clean src/main/resources")
    }

    makeTask[ExtractTask]("extractMinecraftSources"){ t =>
      t.include(this.javaFiles: _*)
      t.from(Constants.REMAPPED_CLEAN)
      t.into(Constants.MINECRAFT_CLEAN_SOURCES)
      t.dependsOn("extractMinecraftResources")
      t.setDescription("Extracts the remapped and decompiled minecraft sources into the clean src/main/java")
    }

    makeTask[ExtractTask]("extractNailedResources"){ t =>
      t.exclude(this.javaFiles: _*)
      t.setIncludeEmptyDirs(includeEmptyDirs = false)
      t.from(Constants.REMAPPED_DIRTY)
      t.into(Constants.MINECRAFT_DIRTY_RESOURCES)
      t.doFirst(new Action[Task] {
        override def execute(p1: Task){
          val files = toDelayedFile(Constants.MINECRAFT_DIRTY_RESOURCES).resolve().listFiles()
          if(files != null) files.foreach(_.delete())
        }
      })
      t.dependsOn("remapDirtySource")
      t.setDescription("Extracts the minecraft resources into the dirty src/main/resources")
    }

    makeTask[CompressLzmaTask]("compressDeobfData"){ t =>
      t.setInput(Constants.NOTCH_2_SRG_SRG)
      t.setOutput(Constants.DEOBF_DATA)
      t.dependsOn("generateMappings")
      t.setDescription("Compresses the notch -> srg .srg file")
    }

    makeTask[Copy]("copyDeobfData"){ t =>
      t.from(toDelayedFile(Constants.DEOBF_DATA))
      t.from(toDelayedFile(Constants.RUNTIME_VERSIONFILE))
      t.into(toDelayedFile(Constants.MINECRAFT_DIRTY_RESOURCES))
      t.dependsOn("extractNailedResources", "compressDeobfData", "generateVersionFile")
      t.setDescription("Copies the compressed .srg file and version info into the dirty src/main/resources")
    }

    makeTask[ExtractTask]("extractNailedSources"){ t =>
      t.include(this.javaFiles: _*)
      t.from(Constants.REMAPPED_DIRTY)
      t.into(Constants.MINECRAFT_DIRTY_SOURCES)
      t.doFirst(new Action[Task] {
        override def execute(p1: Task){
          val files = toDelayedFile(Constants.MINECRAFT_DIRTY_SOURCES).resolve().listFiles()
          if(files != null) files.foreach(_.delete())
        }
      })
      t.dependsOn("copyDeobfData")
      t.setDescription("Extracts the remapped and decompiled minecraft sources into the dirty src/main/java")
    }

    makeTask[GenerateVersionFileTask]("generateVersionFile"){ t =>
      t.setInfoFile(Constants.VERSION_INFO)
      t.setOutput(Constants.RUNTIME_VERSIONFILE)
      t.addConfiguration(mcCfg)
      t.addConfiguration(nailedCfg)
      t.getOutputs.upToDateWhen(Constants.CALL_FALSE)
      t.setDescription("Generates the version.json file that contains information about the version and dependencies")
    }

    makeTask[ExtractRangeMapTask]("generateRangeMap"){ t =>
      t.addConfiguration(mcCfg)
      t.addConfiguration(nailedCfg)
      t.addInput(Constants.MINECRAFT_DIRTY_SOURCES)
      t.setRangeMap(Constants.RANGEMAP)
      t.setStaticsList(Constants.STATICS_LIST)
      t.setCleanCompiled(Constants.JAR_SRG)
      t.dependsOn("deobfuscate")
      t.setDescription("Generates the rangemaps used for remapping the sourcecode from mapped names back to srg")
    }

    makeTask[ApplySrg2SourceTask]("retroMapSources"){ t =>
      t.addInput(Constants.MINECRAFT_DIRTY_SOURCES)
      t.setOutput(Constants.DIRTY_REMAPPED_SRC)
      t.addSrg(toDelayedFile(Constants.MCP_2_SRG_SRG))
      t.addExc(toDelayedFile(Constants.MCP_EXC))
      t.addExc(toDelayedFile(Constants.SRG_EXC))
      t.setRangeMap(Constants.RANGEMAP)
      t.setStaticsList(Constants.STATICS_LIST)
      t.dependsOn("generateMappings", "generateRangeMap")
      t.setDescription("Uses the generated rangemaps to remap the mapped sourcecode back to srg")
    }

    makeTask[GeneratePatchesTask]("generatePatches"){ t =>
      t.setPatchDir(Constants.NAILED_PATCH_DIR)
      t.setOriginal(Constants.ZIP_DECOMP)
      t.setChanged(Constants.DIRTY_REMAPPED_SRC)
      t.setOriginalPrefix("../src-base/minecraft")
      t.setChangedPrefix("../src-work/minecraft")
      t.setGroup("Nailed-MCP")
      t.dependsOn("retroMapSources")
      t.setDescription("Generates patches from the difference between the dirty source and the clean source")
    }

    makeTask[ReobfuscateTask]("reobfuscate"){ t =>
      t.setSrg(Constants.MCP_2_NOTCH_SRG)
      t.setExc(Constants.SRG_EXC)
      t.setReverse(reverse = false)
      t.setPreFFJar(Constants.JAR_SRG)
      t.setOutJar(Constants.REOBFUSCATED)
      t.setMethodCsv(Constants.METHODS_CSV)
      t.setFieldCsv(Constants.FIELDS_CSV)
      t.dependsOn("jar", "extractNailedSources", "generateMappings")
      t.setDescription("Reobfuscates the nailed code to use obfuscated names")
    }

    makeTask[GenerateBinaryPatchesTask]("generateBinaryPatches"){ t =>
      t.setDirtyJar(Constants.REOBFUSCATED)
      t.setOutJar(Constants.BINPATCHES)
      t.setDeobfuscationData(Constants.DEOBF_DATA)
      t.setSrg(Constants.NOTCH_2_SRG_SRG)
      t.addPatchList(toDelayedFileTree(Constants.NAILED_PATCH_DIR))
      t.dependsOn("reobfuscate", "compressDeobfData")
      t.setDescription("Checks the binary difference between the compiled dirty source and the clean source, and writes it to the patch file")
    }

    makeTask[Jar]("packageServer"){ t =>
      t.getOutputs.upToDateWhen(Constants.CALL_FALSE)
      t.from(toDelayedZipFileTree(Constants.BINPATCHES))
      t.from(new Delayed[FileTree](null, project) {
        override def resolve(): FileTree = project.zipTree(apiProject.getTasks.getByName("jar").property("archivePath"))
      })
      t.from(toDelayedFileTree(Constants.NAILED_RESOURCES))
      t.from(toDelayedFile(Constants.RUNTIME_VERSIONFILE))
      t.from(toDelayedFile(Constants.DEOBF_DATA))
      t.setIncludeEmptyDirs(false)
      t.dependsOn("generateBinaryPatches", /*"createChangelog",*/ "generateVersionFile", ":api:jar")
      project.getArtifacts.add("archives", t)
      t.setDescription("Packages everything needed at runtime to run the server")
    }

    makeTask[Jar]("packageJavadoc"){ t =>
      t.getOutputs.upToDateWhen(Constants.CALL_FALSE)
      t.setClassifier("javadoc")
      t.from("build/docs/javadoc")
      t.dependsOn("javadoc")
      project.getArtifacts.add("archives", t)
      t.setDescription("Packages the javadoc")
    }

    makeTask[Jar]("packageSource"){ t =>
      t.getOutputs.upToDateWhen(Constants.CALL_FALSE)
      t.setClassifier("sources")
      t.from(toDelayedFileTree(Constants.NAILED_JAVA_SOURCES))
      t.from(toDelayedFileTree(Constants.NAILED_RESOURCES))
      t.from(toDelayedFileTree(Constants.NAILED_JAVA_API_SOURCES))
      t.from(toDelayedFileTree(Constants.NAILED_API_RESOURCES))
      project.getArtifacts.add("archives", t)
      t.setDescription("Packages all sourcecode")
    }

    metaTask("setupNailed"){ t =>
      t.dependsOn("extractNailedSources", "extractMinecraftSources")
      t.setGroup("Nailed-MCP")
      t.setDescription("Decompiles minecraft and sets up the development environment")
    }

    metaTask("buildPackages"){ t =>
      t.dependsOn("packageServer", "packageJavadoc", "packageSource").setGroup("Nailed-MCP")
      t.setGroup("Nailed-MCP")
      t.setDescription("Builds all packages")
    }

    project.getTasks.getByName("uploadArchives").dependsOn("buildPackages")

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

    //project.getDependencies.add("compile", project.getDependencies.module(apiProject))
  }

  def afterEvaluate(project: Project){
    val reader = new FileReader(new DelayedFile(Constants.VERSION_INFO, project).call())
    val json = new JsonParser().parse(reader).getAsJsonObject
    reader.close()

    val deps = project.getDependencies
    for(dep <- json.getAsJsonArray("dependencies")){
      deps.add(Constants.MINECRAFT_CONFIGURATION, dep.getAsString)
    }

    val ext = project.getExtensions.getByName(Constants.MCP_EXTENSION_NAME).asInstanceOf[NailedMCPExtension]

    if(ext.mappingsSet){
      project.getDependencies.add(Constants.MAPPINGS_CONFIGURATION, ImmutableMap.of(
        "group", "de.oceanlabs.mcp",
        "name", "mcp_" + ext.getMappingsChannel,
        "version", ext.getMappingsVersion + "-" + ext.getMinecraftVersion,
        "ext", "zip"
      ))
    }

    //Add extra srg
    val task = project.getTasks.getByName("reobfuscate").asInstanceOf[ReobfuscateTask]
    task.setExtraSrg(ext.getExtraSrg)

    project.getDependencies.add(Constants.MCJAR_CONFIGURATION, s"net.minecraft:minecraft_server:${ext.getMinecraftVersion}@jar")
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

  def addMavenRepo(project: Project)(configure: (MavenArtifactRepository) => Unit){
    project.getRepositories.maven(new Action[MavenArtifactRepository] {
      override def execute(repo: MavenArtifactRepository) = configure(repo)
    })
  }

  def addIvyRepo(project: Project)(configure: (IvyArtifactRepository) => Unit){
    project.getRepositories.ivy(new Action[IvyArtifactRepository] {
      override def execute(repo: IvyArtifactRepository) = configure(repo)
    })
  }

  //@inline def metaTask(name: String): DefaultTask = makeTask[DefaultTask](this.project, name){t => }
  @inline def metaTask(name: String)(configure: (DefaultTask) => Unit): DefaultTask = makeTask[DefaultTask](this.project, name)(configure)
  @inline def makeTask[T <: Task](name: String)(configure: (T) => Unit)(implicit mf: Manifest[T]): T = makeTask[T](this.project, name)(configure)(mf)
  def makeTask[T <: Task](project: Project, name: String)(configure: (T) => Unit)(implicit mf: Manifest[T]): T = {
    val map = new util.HashMap[String, AnyRef]()
    map.put("name", name)
    map.put("type", mf.runtimeClass)
    val t = project.task(map, name).asInstanceOf[T]
    configure(t)
    t
  }

  implicit def toDelayedString(s: String): DelayedString = new DelayedString(s, this.project)
  implicit def toDelayedFile(s: String): DelayedFile = new DelayedFile(s, this.project)
  def toDelayedFileTree(s: String): DelayedFileTree = new DelayedFileTree(s, this.project)
  def toDelayedZipFileTree(s: String): DelayedFileTree = new DelayedFileTree(s, this.project, true)
}
