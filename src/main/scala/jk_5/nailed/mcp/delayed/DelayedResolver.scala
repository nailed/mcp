package jk_5.nailed.mcp.delayed

import org.gradle.api.Project
import jk_5.nailed.mcp.{NailedMCPExtension, Constants}
import java.io.File

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

    p = p.replace("{CACHE_DIR}", project.getGradle.getGradleUserHomeDir.getAbsolutePath.replace('\\', '/') + "/caches")
    p = p.replace("{MAPPINGS_DIR}", project.getRootDir.getAbsolutePath.replace('\\', '/') + "/mappings")
    p = p.replace("{BUILD_DIR}", project.getBuildDir.getAbsolutePath.replace('\\', '/'))
    p = p.replace("{MC_VERSION}", ext.getMinecraftVersion)

    project.getLogger.info("Resolved: {}", p)

    p
  }
}
