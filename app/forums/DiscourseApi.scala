package forums

import java.sql.Timestamp
import java.text.SimpleDateFormat
import java.util.concurrent.TimeUnit

import models.user.User
import ore.permission.role.RoleTypes
import ore.permission.role.RoleTypes.RoleType
import play.api.libs.json.JsObject
import play.api.libs.ws.{WSClient, WSResponse}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

/**
  * A DiscourseApi that depends on the OreModelService.
  */
trait DiscourseApi {

  /** Handles Discourse post embedding */
  val embed: DiscourseEmbeddingService

  /** The date format for incoming responses */
  val DateFormat = new SimpleDateFormat("yyy-MM-dd'T'HH:mm:ss.SSS'Z'")

  /** The base URL */
  val url: String

  /** The super secret API key */
  val key: String

  /** The username of an administrator */
  val admin: String

  protected val ws: WSClient

  /**
    * Returns true if the Discourse instance is available.
    *
    * @return True if available
    */
  def isAvailable: Boolean = {
    Await.result(this.ws.url(this.url).get().map(_ => true).recover {
      case e: Exception => false
    }, Duration(10000, TimeUnit.MILLISECONDS))
  }

  /** Returns a user URL for the specified username. */
  def userUrl(username: String): String = url + "/users/" + username + ".json"

  /**
    * Attempts to retrieve the user with the specified username from the forums
    * and creates them if they exist.
    *
    * @param username Username to find
    * @return         New user or None
    */
  def fetchUser(username: String): Future[Option[User]] = {
    this.ws.url(userUrl(username)).get.map { response =>
      validate(response) { json =>
        val userObj = (json \ "user").as[JsObject]
        new User(
          id            =   (userObj \ "id").asOpt[Int],
          _name         =   (userObj \ "name").asOpt[String],
          _username     =   (userObj \ "username").as[String],
          _email        =   (userObj \ "email").asOpt[String],
          _joinDate     =   (userObj \ "created_at").asOpt[String]
            .map(jd => new Timestamp(DateFormat.parse(jd).getTime)),
          _avatarUrl    =   (userObj \ "avatar_template").asOpt[String],
          _globalRoles  =   parseRoles(userObj).toList)
      }
    }
  }

  /**
    * Creates a new user on the forums with the given parameters.
    *
    * @param name     User's full name
    * @param username User's username
    * @param email    User's email
    * @param password User's password
    * @return         ID of new user if created, None otherwise
    */
  def createUser(name: String, username: String, email: String, password: String): Future[Option[Int]] = {
    val url = this.url + "/users?api_key=" + this.key + "&api_username=" + this.admin
    val data = Map(
      "name" -> Seq(name),
      "username" -> Seq(username),
      "email" -> Seq(email),
      "password" -> Seq(password),
      "active" -> Seq("true")
    )
    this.ws.url(url).post(data).map(response => validate(response)(json => (json \ "user_id").as[Int]))
  }

  /**
    * Adds a group to the specified user.
    *
    * @param userId   User ID
    * @param groupId  ID of group to add
    * @return         True if successful
    */
  def addUserGroup(userId: Int, groupId: Int) = {
    val url = this.url + "/admin/users/" + userId + "/groups?api_key=" + this.key + "&api_username=" + this.admin
    val data = Map("group_id" -> Seq(groupId.toString))
    this.ws.url(url).post(data)
  }

  /**
    * Returns the URL to the specified user's avatar image.
    *
    * @param username Username to get avatar URL for
    * @param size     Size of avatar
    * @return         Avatar URL
    */
  def fetchAvatarUrl(username: String, size: Int): Future[Option[String]] = {
    this.ws.url(userUrl(username)).get.map { response =>
      validate(response) { json =>
        val template = (json \ "user" \ "avatar_template").as[String]
        this.url + template.replace("{size}", size.toString)
      }
    }
  }

  /**
    * Builds a RoleType Set from the specified User JSON object.
    *
    * @param userObj  User JsObject
    * @return         Set of RoleTypes
    */
  def parseRoles(userObj: JsObject): Set[RoleType] = {
    val groups = (userObj \ "groups").as[List[JsObject]]
    (for (group <- groups) yield {
      val id = (group \ "id").as[Int]
      RoleTypes.values.find(_.roleId == id)
    }).flatten.map(_.asInstanceOf[RoleType]).toSet
  }

  /**
    * Validates an incoming Discourse API response.
    *
    * @param response Response to validate
    * @param f        Function to run if validated
    * @tparam A       Return type
    * @return         Return type
    */
  def validate[A](response: WSResponse)(f: JsObject => A): Option[A] = {
    val json = response.json.as[JsObject]
    if (!json.keys.contains("errors")) Some(f(json)) else None
  }

}
