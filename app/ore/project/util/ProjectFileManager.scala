package ore.project.util

import java.nio.file.Files._
import java.nio.file.Path

import models.project.Project
import util.OreEnv

import scala.util.Try
import collection.JavaConverters._

/**
  * Handles file management of Projects.
  */
class ProjectFileManager(val env: OreEnv) {

  /**
    * Returns the specified project's plugin directory.
    *
    * @param owner  Owner name
    * @param name   Project name
    * @return       Plugin directory
    */
  def getProjectDir(owner: String, name: String): Path = getUserDir(owner).resolve(name)

  /**
    * Returns the specified user's plugin directory.
    *
    * @param user User name
    * @return     Plugin directory
    */
  def getUserDir(user: String): Path = this.env.plugins.resolve(user)

  /**
    * Renames this specified project in the file system.
    *
    * @param owner    Owner name
    * @param oldName  Old project name
    * @param newName  New project name
    * @return         New path
    */
  def renameProject(owner: String, oldName: String, newName: String): Try[Unit] = Try {
    val newProjectDir = getProjectDir(owner, newName)
    move(getProjectDir(owner, oldName), newProjectDir)
    // Rename plugin files
    for (channelDir <- newProjectDir.toFile.listFiles()) {
      if (channelDir.isDirectory) {
        for (pluginFile <- channelDir.listFiles()) {
          move(pluginFile.toPath, getProjectDir(owner, newName).resolve(pluginFile.getName))
        }
      }
    }
  }

  /**
    * Returns the directory that contains a [[Project]]'s custom icons.
    *
    * @param owner  Project owner
    * @param name   Project name
    * @return       Icons directory path
    */
  def getIconsDir(owner: String, name: String): Path = getProjectDir(owner, name).resolve("icons")

  /**
    * Returns the directory that contains a [[Project]]'s main icon.
    *
    * @param owner  Project owner
    * @param name   Project name
    * @return       Icon directory path
    */
  def getIconDir(owner: String, name: String): Path = getIconsDir(owner, name).resolve("icon")

  /**
    * Returns the path to a custom [[Project]] icon, if any, None otherwise.
    *
    * @param project Project to get icon for
    * @return Project icon
    */
  def getIconPath(project: Project): Option[Path] = findFirstFile(getIconDir(project.ownerName, project.name))

  /**
    * Returns the directory that contains an icon that has not yet been saved.
    *
    * @param owner  Project owner
    * @param name   Project name
    * @return       Pending icon path
    */
  def getPendingIconDir(owner: String, name: String): Path = getIconsDir(owner, name).resolve("pending")

  /**
    * Returns the directory to a custom [[Project]] icon that has not yet been
    * saved.
    *
    * @param project Project to get icon for
    * @return Pending icon path
    */
  def getPendingIconPath(project: Project): Option[Path]
  = findFirstFile(getPendingIconDir(project.ownerName, project.name))

  private def findFirstFile(dir: Path): Option[Path] = {
    if (exists(dir))
      list(dir).iterator.asScala.filterNot(isDirectory(_)).toStream.headOption
    else
      None
  }

}
