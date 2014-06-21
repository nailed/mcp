package jk_5.nailed.mcp

import org.gradle.api._
import _root_.java.util
import jk_5.nailed.mcp.tasks.common.DownloadTask
import jk_5.nailed.mcp.delayed.{DelayedFile, DelayedString}
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import jk_5.nailed.mcp.tasks._
import scala.collection.convert.wrapAsScala._

/**
 * No description given
 *
 * @author jk-5
 */
class McpPlugin extends Plugin[Project] {

  var project: Project = _

  override def apply(project: Project){
    this.project = project

    project.afterEvaluate(new Action[Project] {
      override def execute(project: Project){
        afterEvaluate(project)
      }
    })

    project.allprojects(new Action[Project] {
      override def execute(project: Project){
        project.getRepositories.mavenCentral
        project.getRepositories.maven(new Action[MavenArtifactRepository]{
          override def execute(repo: MavenArtifactRepository){
            repo.setName("reening")
            repo.setUrl("http://maven.reening.nl")
          }
        })
      }
    })

    project.getExtensions.create(Constants.MCP_EXTENSION_NAME, classOf[NailedMCPExtension], project)

    project.getConfigurations.create(Constants.FERNFLOWER_CONFIGURATION)
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
      //t.addAccessTransformer(Constants.NAILED_RESOURCES + "/nailed_at.cfg")
      t.setApplyMarkers(applyMarkers = true)
      t.setFieldCsv(Constants.FIELDS_CSV)
      t.setMethodCsv(Constants.METHODS_CSV)
      t.dependsOn("removeShadedLibs", "generateMappings")
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
      //t.dependsOn("decompile")
    }

    /*makeTask("patchSource", classOf[PatchSourceTask]){ t =>
      t.dependsOn("decompile")
    }*/

    /*makeTask("remapDirtySource", classOf[RemapSourceTask]){ t =>
      t.dependsOn("patchSource")
    }*/

    makeTask("setupNailed", classOf[DefaultTask]){ t =>

    }
  }

  def afterEvaluate(project: Project){

  }

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
