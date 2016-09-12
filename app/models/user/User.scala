package models.user

import java.sql.Timestamp

import com.google.common.base.Preconditions._
import db.action.ModelAccess
import db.impl.ModelKeys._
import db.impl.OrePostgresDriver.api._
import db.impl._
import db.impl.action.{ProjectActions, UserActions}
import db.meta._
import models.project.{Flag, Project}
import ore.UserOwned
import ore.permission._
import ore.permission.role.RoleTypes.{DonorType, RoleType}
import ore.permission.role._
import ore.permission.scope.{GlobalScope, ProjectScope, Scope, ScopeSubject}
import util.StringUtils._

import scala.annotation.meta.field

/**
  * Represents a Sponge user.
  *
  * @param id           External ID provided by authentication.
  * @param createdAt    Date this user first logged onto Ore.
  * @param _name        Full name of user
  * @param _username    Username
  * @param _email       Email
  * @param _tagline     The user configured "tagline" displayed on the user page.
  */
@Actions(classOf[UserActions])
@HasMany(Array(classOf[Project], classOf[ProjectRole], classOf[Flag]))
case class User(override val id: Option[Int] = None,
                override val createdAt: Option[Timestamp] = None,
                @(Bind @field) private var _name: Option[String] = None,
                @(Bind @field) private var _username: String,
                @(Bind @field) private var _email: Option[String] = None,
                @(Bind @field) private var _tagline: Option[String] = None,
                @(Bind @field) private var _globalRoles: List[RoleType] = List(),
                @(Bind @field) private var _joinDate: Option[Timestamp] = None,
                @(Bind @field) private var _avatarUrl: Option[String] = None)
                extends OreModel(id, createdAt)
                  with UserLike
                  with UserOwned
                  with ScopeSubject {

  override type M = User
  override type T = UserTable
  override type A = UserActions

  /**
    * The User's [[PermissionPredicate]]. All permission checks go through
    * here.
    */
  val can: PermissionPredicate = PermissionPredicate(this)
  val cannot: PermissionPredicate = PermissionPredicate(this, not = true)

  /**
    * Returns this User's full name.
    *
    * @return Full name of user
    */
  def name: Option[String] = this._name

  /**
    * Sets this User's full name.
    *
    * @param _fullName Full name of user
    */
  def name_=(_fullName: String) = {
    this._name = Option(_fullName)
    if (isDefined) update(Name)
  }

  /**
    * Sets this User's username.
    *
    * @param _username Username of User
    */
  def username_=(_username: String) = {
    checkNotNull(_username, "username cannot be null", "")
    this._username = _username
    if (isDefined) update(Username)
  }

  /**
    * Returns this User's email.
    *
    * @return User email
    */
  def email: Option[String] = this._email

  /**
    * Sets this User's email.
    *
    * @param _email User email
    */
  def email_=(_email: String) = {
    this._email = Option(_email)
    if (isDefined) update(Email)
  }

  /**
    * Sets the Timestamp instant when this User joined Sponge for the first
    * time.
    *
    * @param _joinDate Sponge join date
    */
  def joinDate_=(_joinDate: Timestamp) = {
    this._joinDate = Option(_joinDate)
    if (isDefined) update(JoinDate)
  }

  /**
    * Sets this User's avatar url.
    *
    * @param _avatarUrl Avatar url
    */
  def avatarUrl_=(_avatarUrl: String) = {
    this._avatarUrl = Option(_avatarUrl)
    if (isDefined) update(AvatarUrl)
  }

  /**
    * Sets this User's "tagline" that is displayed on the User page.
    *
    * @param _tagline Tagline to display
    */
  def tagline_=(_tagline: String) = {
    checkArgument(_tagline.length <= config.users.getInt("max-tagline-len").get, "tagline too long", "")
    this._tagline = Option(nullIfEmpty(_tagline))
    if (isDefined) update(Tagline)
  }

  /**
    * Returns the Project with the specified name that this User owns.
    *
    * @param name   Name of project
    * @return       Owned project, if any, None otherwise
    */
  def getProject(name: String): Option[Project] = this.projects.find(_.userId === this.id.get)

  /**
    * Returns a [[ModelAccess]] of [[ProjectRole]]s.
    *
    * @return ProjectRoles
    */
  def projectRoles = this.getMany[ProjectRoleTable, ProjectRole](classOf[ProjectRole])

  /**
    * Sets the [[RoleTypes]]s that this User has globally.
    *
    * @param _globalRoles Roles to set
    */
  def globalRoles_=(_globalRoles: Set[RoleType]) = {
    this._globalRoles = _globalRoles.toList
    if (isDefined) update(GlobalRoles)
  }

  /**
    * Returns the highest level [[DonorType]] this User has.
    *
    * @return Highest level donor type
    */
  def donorType: Option[DonorType] = {
    this.globalRoles.toList
      .filter(_.isInstanceOf[DonorType])
      .map(_.asInstanceOf[DonorType])
      .sortBy(_.id).lastOption
  }

  /**
    * Returns this User's highest level of Trust.
    *
    * @return Highest level of trust
    */
  def trustIn(scope: Scope = GlobalScope): Trust = Defined {
    scope match {
      case GlobalScope => this.globalRoles.map(_.trust).toList.sorted.reverse.headOption.getOrElse(Default)
      case pScope: ProjectScope =>
        this.projectRoles.find(_.projectId === pScope.projectId).map(_.roleType.trust).getOrElse(Default)
    }
  }

  /**
    * Returns the [[Flag]]s submitted by this User.
    *
    * @return Flags submitted by user
    */
  def flags = this.getMany[FlagTable, Flag](classOf[Flag])

  /**
    * Returns true if the User has an unresolved [[Flag]] on the specified
    * [[Project]].
    *
    * @param project  Project to check
    * @return         True if has pending flag on Project
    */
  def hasUnresolvedFlagFor(project: Project): Boolean
  = this.flags.exists(f => f.projectId === project.id.get && !f.isResolved)

  /**
    * Returns the Projects that this User has starred.
    *
    * @param page Page of user stars
    * @return     Projects user has starred
    */
  def starred(page: Int = -1): Seq[Project] = Defined {
    val starsPerPage = config.users.getInt("stars-per-page").get
    val limit = if (page < 1) -1 else starsPerPage
    val actions = service.getActions(classOf[ProjectActions])
    service.await(actions.starredBy(this.id.get, limit, (page - 1) * starsPerPage)).get
  }

  /**
    * Fills the mutable field in this User with the specified User's
    * non-missing mutable fields.
    *
    * @param user User to fill with
    * @return     This user
    */
  def fill(user: User): User = {
    if (user == null) return this
    user.name.foreach(this.name_=)
    user.email.foreach(this.email_=)
    user.tagline.foreach(this.tagline_=)
    user.joinDate.foreach(this.joinDate_=)
    user._avatarUrl.foreach(this.avatarUrl_=)
    this.username = user.username
    this.globalRoles = user.globalRoles
    this
  }

  override def username: String = this._username
  override def globalRoles: Set[RoleType] = this._globalRoles.toSet
  override def avatarTemplate: Option[String] = this._avatarUrl.map(this.config.forums.getString("baseUrl").get + _)
  override def tagline: Option[String] = this._tagline
  override def joinDate: Option[Timestamp] = this._joinDate

  override val scope = GlobalScope
  override def userId = this.id.get
  override def copyWith(id: Option[Int], theTime: Option[Timestamp]) = this.copy(createdAt = theTime)

}
