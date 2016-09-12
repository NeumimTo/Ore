package db.impl.service

import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}

import db.action.ModelActions
import db.impl.OrePostgresDriver.api._
import db.impl.OreTypeSetters._
import db.impl.action._
import db.impl.{ChannelTable, OrePostgresDriver}
import db.{ModelRegistry, ModelService}
import forums.DiscourseApi
import models.project.Channel
import ore.Colors.Color
import ore.permission.role.RoleTypes.RoleType
import ore.project.Categories.Category
import ore.project.FlagReasons.FlagReason
import play.api.db.slick.DatabaseConfigProvider
import slick.driver.JdbcProfile
import util.OreConfig

import scala.concurrent.duration.Duration

/**
  * The Ore ModelService implementation. Contains registration of Ore-specific
  * types and Models.
  *
  * @param db DatabaseConfig
  */
@Singleton
class OreModelService @Inject()(config: OreConfig,
                                forums: DiscourseApi,
                                db: DatabaseConfigProvider)
                                extends ModelService {

  override lazy val registry = new ModelRegistry {}
  override lazy val processor = new OreModelProcessor(this, this.users, this.projects, this.config, this.forums)
  override lazy val driver = OrePostgresDriver
  override lazy val DB = db.get[JdbcProfile]
  override lazy val DefaultTimeout: Duration = Duration(config.app.getInt("db.default-timeout").get, TimeUnit.SECONDS)

  import registry.{registerActions, registerModelBase, registerTypeSetter}

  val users = registerModelBase[UserBase](classOf[UserBase], new UserBase(this, forums, config))
  val projects = registerModelBase[ProjectBase](classOf[ProjectBase], new ProjectBase(this))
  val orgs = registerModelBase[OrganizationBase](classOf[OrganizationBase], new OrganizationBase(this, forums, config))

  // Custom types
  registerTypeSetter(classOf[Color], ColorTypeSetter)
  registerTypeSetter(classOf[RoleType], RoleTypeTypeSetter)
  registerTypeSetter(classOf[List[RoleType]], RoleTypeListTypeSetter)
  registerTypeSetter(classOf[Category], CategoryTypeSetter)
  registerTypeSetter(classOf[FlagReason], FlagReasonTypeSetter)

  // Ore models
  registerActions(new ModelActions[ChannelTable, Channel](this, classOf[Channel], TableQuery[ChannelTable]))
  registerActions(new PageActions(this))
  registerActions(new ProjectActions(this))
  registerActions(new UserActions(this))
  registerActions(new VersionActions(this))
  registerActions(new OrganizationActions(this))

}
