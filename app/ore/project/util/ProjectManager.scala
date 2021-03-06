package ore.project.util

import java.nio.file.Files._
import javax.inject.Inject

import com.google.common.base.Preconditions._
import db.ModelService
import db.impl.OrePostgresDriver.api._
import db.impl.service.{ProjectBase, UserBase}
import forums.DiscourseApi
import models.project.{Channel, Project, Version}
import org.apache.commons.io.FileUtils
import util.StringUtils._
import util.{OreConfig, OreEnv}

/**
  * Handles management tasks for [[models.project.Project]]s and their components.
  */
trait ProjectManager {

  val service: ModelService
  val env: OreEnv
  val fileManager: ProjectFileManager = new ProjectFileManager(this.env)

  implicit val users: UserBase = this.service.access(classOf[UserBase])
  implicit val projects: ProjectBase = this.service.access(classOf[ProjectBase])

  implicit val config: OreConfig
  implicit val forums: DiscourseApi

  /**
    * Renames the specified [[Project]].
    *
    * @param project  Project to rename
    * @param name     New name to assign Project
    */
  def renameProject(project: Project, name: String) = {
    val newName = compact(name)
    val newSlug = slugify(newName)
    checkArgument(this.config.isValidProjectName(name), "invalid name", "")
    checkArgument(this.projects.isNamespaceAvailable(project.ownerName, newSlug), "slug not available", "")

    this.fileManager.renameProject(project.ownerName, project.name, newName)
    project.name = newName
    project.slug = newSlug

    if (project.topicId.isDefined) {
      this.forums.embed.renameTopic(project)
      this.forums.embed.updateTopic(project)
    }
  }

  /**
    * Saves any pending icon that has been uploaded for the specified [[Project]].
    *
    * @param project Project to save icon for
    */
  def savePendingIcon(project: Project) = {
    this.fileManager.getPendingIconPath(project).foreach { iconPath =>
      val iconDir = this.fileManager.getIconDir(project.ownerName, project.name)
      if (notExists(iconDir))
        createDirectories(iconDir)
      FileUtils.cleanDirectory(iconDir.toFile)
      move(iconPath, iconDir.resolve(iconPath.getFileName))
    }
  }

  /**
    * Irreversibly deletes this project.
    *
    * @param project Project to delete
    */
  def deleteProject(project: Project) = {
    FileUtils.deleteDirectory(this.fileManager.getProjectDir(project.ownerName, project.name).toFile)
    if (project.topicId.isDefined) forums.embed.deleteTopic(project)
    project.remove()
  }

  /**
    * Irreversibly deletes this channel and all version associated with it.
    *
    * @param context Project context
    */
  def deleteChannel(channel: Channel)(implicit context: Project = null) = {
    val proj = if (context != null) context else channel.project
    checkArgument(proj.id.get == channel.projectId, "invalid project id", "")

    val channels = proj.channels.all
    checkArgument(channels.size > 1, "only one channel", "")
    checkArgument(channel.versions.isEmpty || channels.count(c => c.versions.nonEmpty) > 1, "last non-empty channel", "")

    FileUtils.deleteDirectory(this.fileManager.getProjectDir(proj.ownerName, proj.name).resolve(channel.name).toFile)
    channel.remove()
  }

  /**
    * Irreversibly deletes this version.
    *
    * @param project Project context
    */
  def deleteVersion(version: Version)(implicit project: Project = null) = {
    val proj = if (project != null) project else version.project
    checkArgument(proj.versions.size > 1, "only one version", "")
    checkArgument(proj.id.get == version.projectId, "invalid context id", "")

    val rv = proj.recommendedVersion
    version.remove()

    // Set recommended version to latest version if the deleted version was the rv
    if (version.equals(rv)) {
      proj.recommendedVersion = proj.versions.sorted(_.createdAt.desc, limit = 1).head
    }

    // Delete channel if now empty
    val channel: Channel = version.channel
    if (channel.versions.isEmpty) this.deleteChannel(channel)

    delete(this.fileManager.getProjectDir(proj.ownerName, proj.name).resolve(version.fileName))
  }

}

case class OreProjectManager @Inject()(override val service: ModelService,
                                       override val env: OreEnv,
                                       override val config: OreConfig,
                                       override val forums: DiscourseApi)
                                       extends ProjectManager
