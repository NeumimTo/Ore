package models.statistic

import java.sql.Timestamp

import com.github.tminglei.slickpg.InetString
import controllers.Requests.ProjectRequest
import db.impl.action.VersionActions
import db.impl.service.UserBase
import db.meta.{Actions, Bind}
import models.project.Version
import ore.StatTracker._

import scala.annotation.meta.field

/**
  * Represents a unique download on a Project Version.
  *
  * @param id         Unique ID of entry
  * @param createdAt  Timestamp instant of creation
  * @param modelId    ID of model the stat is on
  * @param address    Client address
  * @param cookie     Browser cookie
  * @param userId     User ID
  */
@Actions(classOf[VersionActions])
case class VersionDownload(override val id: Option[Int] = None,
                           override val createdAt: Option[Timestamp] = None,
                           override val modelId: Int,
                           override val address: InetString,
                           override val cookie: String,
                           @(Bind @field) private var userId: Option[Int] = None)
                           extends StatEntry[Version](id, createdAt, modelId, address, cookie, userId) {
  override def copyWith(id: Option[Int], theTime: Option[Timestamp]) = this.copy(id = id, createdAt = theTime)
}

object VersionDownload {

  /**
    * Creates a new VersionDownload to be (or not be) recorded from an incoming
    * request.
    *
    * @param version  Version downloaded
    * @param request  Request to bind
    * @return         New VersionDownload
    */
  def bindFromRequest(version: Version)(implicit request: ProjectRequest[_], users: UserBase): VersionDownload = {
    val userId = users.current(request.session).flatMap(_.id)
    val dl = VersionDownload(
      modelId = version.id.get,
      address = InetString(remoteAddress),
      cookie = getStatCookie,
      userId = userId
    )
    dl.userBase = users
    dl
  }

}
