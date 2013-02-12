package models

import play.api.Play.current
import java.util.Date
import com.novus.salat._
import com.novus.salat.annotations._
import com.novus.salat.dao._
import com.mongodb.casbah.Imports._
import MongoContext._

case class User (
  id: ObjectId = new ObjectId,
  username: String,
  password: String,
  address: Option[Address] = None,
  added: Date = new Date(),
  updated: Option[Date] = None,
  deleted: Option[Date] = None,
  friends: Option[List[String]] = None,
  @Key("company_id") company: ObjectId = new ObjectId
)

object User extends ModelCompanion[User, ObjectId] {
  val collection = MongoConnection()("test")("users")
  val dao = new SalatDAO[User, ObjectId](collection) {}

  def findOneByUsername(username: String): Option[User] = dao.findOne(MongoDBObject("username" -> username))
  def findByCountry(country: String) = dao.find(MongoDBObject("address.country" -> country))
}