package jk_5.nailed.mcp.delayed

import jk_5.nailed.mcp.{Constants, NailedMCPExtension}
import org.gradle.api.Project

/**
 * No description given
 *
 * @author jk-5
 */
object DelayedResolver {

  def resolve(pattern: String, project: Project): String = {
    if(pattern == null) return pattern
    var p = pattern

    project.getLogger.info("Resolving: {}", p)

    val ext = project.getExtensions.getByName(Constants.MCP_EXTENSION_NAME).asInstanceOf[NailedMCPExtension]

    p = p.replace("{MAPPINGS_DIR}", "{PROJECT_DIR}/config")
    p = p.replace("{BUILD_DIR}", project.getBuildDir.getAbsolutePath.replace('\\', '/'))
    p = p.replace("{PROJECT_DIR}", project.getRootDir.getAbsolutePath.replace('\\', '/'))
    if(ext.getMinecraftVersion != null){
      p = p.replace("{MC_VERSION}", ext.getMinecraftVersion)
    }

    project.getLogger.info("Resolved: {}", p)

    p
  }
}
