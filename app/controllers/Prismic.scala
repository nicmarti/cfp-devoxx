package controllers

import play.api._
import play.api.mvc._

import play.api.libs.ws._
import play.api.libs.json._

import scala.concurrent._
import play.api.libs.concurrent.Execution.Implicits._

import Play.current

import io.prismic._

/**
 * This Prismic object contains several helpers that make it easier
 * to build your application using both Prismic.io and Play:
 *
 * It reads some configuration from the application.conf file.
 *
 * The debug and error messages emitted by the Scala Kit are redirected
 * to the Play application Logger.
 *
 * It provides an "Action builder" that help to create actions that will query
 * a prismic.io repository.
 *
 * It provides some ready-to-use actions for the OAuth2 workflow.
 */
object Prismic extends Controller {

  // -- Define the key name to use for storing the Prismic.io access token into the Play session
  private val ACCESS_TOKEN = "ACCESS_TOKEN"

  // -- Cache to use (default to keep 200 JSON responses in a LRU cache)
  private val Cache = BuiltInCache(200)

  // -- Write debug and error messages to the Play `prismic` logger (check the configuration in application.conf)
  private val Logger = (level: Symbol, message: String) => level match { 
    case 'DEBUG => play.api.Logger("prismic").debug(message)
    case 'ERROR => play.api.Logger("prismic").error(message)
    case _ => play.api.Logger("prismic").info(message)
  }
  
  // Helper method to read the Play application configuration
  private def config(key: String) = Play.configuration.getString(key).getOrElse(sys.error(s"Missing configuration [$key]"))

  // Compute the callback URL to use for the OAuth worklow
  private def callbackUrl(implicit rh: RequestHeader) = routes.Prismic.callback(code = None, redirect_uri = rh.headers.get("referer")).absoluteURL()

  // -- Define a `Prismic request` that contain both the original request and the Prismic call context
  case class Request[A](request: play.api.mvc.Request[A], ctx: Context) extends WrappedRequest(request)

  // -- A Prismic context that help to keep the reference to useful primisc.io contextual data
  case class Context(api: Api, ref: String, accessToken: Option[String], linkResolver: DocumentLinkResolver) {
    def maybeRef = Option(ref).filterNot(_ == api.master.ref)
    def hasPrivilegedAccess = accessToken.isDefined
  }
  
  object Context {
    implicit def fromRequest(implicit req: Request[_]): Context = req.ctx
  }

  // -- Build a Prismic context
  def buildContext(ref: Option[String])(implicit request: RequestHeader) = {
    for {
      api <- apiHome(request.session.get(ACCESS_TOKEN).orElse(Play.configuration.getString("prismic.token")))
    } yield {
      Context(api, ref.map(_.trim).filterNot(_.isEmpty).getOrElse(api.master.ref), request.session.get(ACCESS_TOKEN), CallForPaper.linkResolver(api, ref.filterNot(_ == api.master.ref))(request))
    }
  }

  // -- Action builder
  def action[A](bodyParser: BodyParser[A])(ref: Option[String] = None)(block: Prismic.Request[A] => Future[SimpleResult]) = Action.async(bodyParser) { implicit request =>
    (
      for {
        ctx <- buildContext(ref)
        result <- block(Request(request, ctx))
      } yield result
    ).recover {
      case ApiError(Error.INVALID_TOKEN, _) => Redirect(routes.Prismic.signin).withNewSession
      case ApiError(Error.AUTHORIZATION_NEEDED, _) => Redirect(routes.Prismic.signin).withNewSession
    }
  }

  // -- Alternative action builder for the default body parser
  def action(ref: Option[String] = None)(block: Prismic.Request[AnyContent] => Future[SimpleResult]): Action[AnyContent] = action(parse.anyContent)(ref)(block)

  // -- Retrieve the Prismic Context from a request handled by an built using Prismic.action
  def ctx(implicit req: Request[_]) = req.ctx
  
  // -- Fetch the API entry document
  def apiHome(accessToken: Option[String] = None) = Api.get(config("prismic.api"), accessToken = accessToken, cache = Cache, logger = Logger)

  // -- Helper: Retrieve a single document by Id
  def getDocument(id: String)(implicit ctx: Prismic.Context): Future[Option[Document]] = {
    for {
      documents <- ctx.api.forms("everything").query(s"""[[:d = at(document.id, "$id")]]""").ref(ctx.ref).submit()
    } yield {
      documents.headOption
    }
  }

  // -- Helper: Retrieve several documents by Id
  def getDocuments(ids: String*)(implicit ctx: Prismic.Context): Future[Seq[Document]] = {
    ids match {
      case Nil => Future.successful(Nil)
      case ids => ctx.api.forms("everything").query(s"""[[:d = any(document.id, ${ids.mkString("[\"","\",\"","\"]")})]]""").ref(ctx.ref).submit()
    }
  }

  // -- Helper: Retrieve a single document from its bookmark
  def getBookmark(bookmark: String)(implicit ctx: Prismic.Context): Future[Option[Document]] = {
    ctx.api.bookmarks.get(bookmark).map(id => getDocument(id)).getOrElse(Future.successful(None))
  }

  // -- Helper: Check if the slug is valid and redirect to the most recent version id needed
  def checkSlug(document: Option[Document], slug: String)(callback: Either[String,Document] => SimpleResult)(implicit r: Prismic.Request[_]) = {
    document.collect {
      case document if document.slug == slug => callback(Right(document))
      case document if document.slugs.contains(slug) => callback(Left(document.slug))
    }.getOrElse {
      CallForPaper.PageNotFound
    }
  }

  // --
  // -- OAuth actions
  // --
  
  def signin = Action.async { implicit req =>
    for(api <- apiHome()) yield {
      Redirect(api.oauthInitiateEndpoint, Map(
        "client_id" -> Seq(config("prismic.clientId")),
        "redirect_uri" -> Seq(callbackUrl),
        "scope" -> Seq("master+releases")
      ))
    }
  }

  def signout = Action {
    Redirect(routes.CallForPaper.index(ref = None)).withNewSession
  }

  def callback(code: Option[String], redirect_uri: Option[String]) = Action.async { implicit req =>
    (
      for {
        api <- apiHome()
        tokenResponse <- WS.url(api.oauthTokenEndpoint).post(Map(
          "grant_type" -> Seq("authorization_code"),
          "code" -> Seq(code.get),
          "redirect_uri" -> Seq(callbackUrl),
          "client_id" -> Seq(config("prismic.clientId")),
          "client_secret" -> Seq(config("prismic.clientSecret"))
        )).filter(_.status == 200).map(_.json)
      } yield { 
        Redirect(redirect_uri.getOrElse(routes.CallForPaper.index(ref = None).url)).withSession(
          ACCESS_TOKEN -> (tokenResponse \ "access_token").as[String]
        )
      }
    ).recover {
      case x: Throwable => 
        Logger('ERROR, s"""Can't retrieve the OAuth token for code $code: ${x.getMessage}""".stripMargin)
        Unauthorized("Can't sign you in")
    }
  }

}