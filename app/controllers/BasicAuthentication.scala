package controllers

import AuthenticationHelpers._
import models.ConferenceDescriptor
import play.api.mvc.Results._
import play.api.mvc.Security.AuthenticatedBuilder

object BasicAuthentication extends AuthenticatedBuilder(
  _.headers.get("Authorization")
    .flatMap(parseAuthHeader)
    .flatMap(validateUser),
  onUnauthorized = { _ =>
    Unauthorized(views.html.defaultpages.unauthorized())
      .withHeaders("WWW-Authenticate" -> """Basic realm="Secured"""")
  }
)

object AuthenticationHelpers {

  val validCredentials = Set(
    Credentials(User(ConferenceDescriptor.gluonUsername()), Password(ConferenceDescriptor.gluonPassword()))
  )

  def authHeaderValue(credentials: Credentials): String =
    "Basic " + org.apache.commons.codec.binary.Base64.encodeBase64(s"${credentials.user.value}:${credentials.password.value}".getBytes)

  def parseAuthHeader(authHeader: String): Option[Credentials] =
    authHeader.split("""\s""") match {
      case Array("Basic", userAndPass) =>
        new String(org.apache.commons.codec.binary.Base64.decodeBase64(userAndPass), "UTF-8").split(":") match {
          case Array(user, password) => Some(Credentials(User(user), Password(password)))
          case _                     => None
        }
      case _ => None
      }

  def validateUser(c: Credentials): Option[User] =
    if (validCredentials.contains(c))
      Some(c.user)
    else
      None
    }

case class Credentials(user: User, password: Password)
case class User(value: String) extends AnyVal
case class Password(value: String) extends AnyVal