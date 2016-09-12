package db.impl.service

import db.impl.OrganizationTable
import db.{ModelBase, ModelService}
import forums.DiscourseApi
import models.user.Organization
import org.apache.commons.lang3.RandomStringUtils
import util.{CryptoUtils, OreConfig, StringUtils}

class OrganizationBase(override val service: ModelService, forums: DiscourseApi, config: OreConfig)
                       extends ModelBase[OrganizationTable, Organization] {

  import this.service.await

  override val modelClass = classOf[Organization]

  /**
    * Creates a new [[Organization]]. This method creates a new user on the
    * forums to represent the Organization.
    *
    * @param name     Organization name
    * @param ownerId  User ID of the organization owner
    * @return         New organization if successful, None otherwise
    */
  def create(name: String, ownerId: Int): Option[Organization] = {
    val password = RandomStringUtils.randomAlphanumeric(this.config.orgs.getInt("passwordLength").get)
    val encryptedPassword = CryptoUtils.encrypt(password, this.config.play.getString("crypto.secret").get)
    val email = name + '@' + this.config.orgs.getString("dummyEmailDomain").get

    await(this.forums.createUser(name, name, email, password)).get.map { userId =>
      await(this.forums.addUserGroup(userId, this.config.orgs.getInt("groupId").get)).get
      val userInfo = await(this.forums.fetchUser(name)).get.get
      val org = new Organization(
        id = Some(userId),
        name = name,
        password = encryptedPassword,
        ownerId = ownerId,
        _avatarUrl = userInfo.avatarUrl,
        _tagline = userInfo.tagline,
        _globalRoles = userInfo.globalRoles.toList
      )
      this.service.access[OrganizationTable, Organization](this.modelClass).add(org)
    }
  }

  /**
    * Returns an [[Organization]] with the specified name if it exists.
    *
    * @param name Organization name
    * @return     Organization with name if exists, None otherwise
    */
  def withName(name: String): Option[Organization] = this.find(StringUtils.equalsIgnoreCase(_.name, name))

}
