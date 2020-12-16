/*
 * Copyright 2020 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package connectors

import config.ApplicationConfig
import connectors.ThirdPartyDeveloperConnector.JsonFormatters._
import connectors.ThirdPartyDeveloperConnector.UnregisteredUserCreationRequest
import domain._
import domain.models.connectors._
import domain.models.developers._
import javax.inject.{Inject, Singleton}
import play.api.http.ContentTypes.JSON
import play.api.http.HeaderNames.{CONTENT_TYPE, CONTENT_LENGTH}
import play.api.http.Status._
import play.api.libs.json.{Format, Json}
import uk.gov.hmrc.http.{UserId => _, _}
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.play.http.metrics.API
import scala.concurrent.{ExecutionContext, Future}
import domain.models.emailpreferences.EmailPreferences
import connectors.ThirdPartyDeveloperConnector.RemoveMfaRequest

@Singleton
class ThirdPartyDeveloperConnector @Inject()(http: HttpClient, encryptedJson: EncryptedJson, config: ApplicationConfig, metrics: ConnectorMetrics
                                            )(implicit val ec: ExecutionContext) extends CommonResponseHandlers {

  import ThirdPartyDeveloperConnector._

  def authenticate(loginRequest: LoginRequest)(implicit hc: HeaderCarrier): Future[UserAuthenticationResponse] = metrics.record(api) {
    encryptedJson.secretRequestJson(
      Json.toJson(loginRequest),
      http.POST(s"$serviceBaseUrl/authenticate", _, Seq(CONTENT_TYPE -> JSON)))
      .map(_.json.as[UserAuthenticationResponse])
      .recover {
        case Upstream4xxResponse(_, UNAUTHORIZED, _, _) => throw new InvalidCredentials
        case Upstream4xxResponse(_, FORBIDDEN, _, _) => throw new UnverifiedAccount
        case Upstream4xxResponse(_, LOCKED, _, _) => throw new LockedAccount
        case _: NotFoundException => throw new InvalidEmail
      }
  }

  def authenticateTotp(totpAuthenticationRequest: TotpAuthenticationRequest)(implicit hc: HeaderCarrier): Future[Session] = metrics.record(api) {
    encryptedJson.secretRequestJson(
      Json.toJson(totpAuthenticationRequest),
      http.POST(s"$serviceBaseUrl/authenticate-totp", _, Seq(CONTENT_TYPE -> JSON)))
      .map(_.json.as[Session])
      .recover {
        case _: BadRequestException => throw new InvalidCredentials
        case _: NotFoundException => throw new InvalidEmail
      }
  }

  lazy val serviceBaseUrl: String = config.thirdPartyDeveloperUrl
  val api = API("third-party-developer")

  def register(registration: Registration)(implicit hc: HeaderCarrier): Future[RegistrationDownstreamResponse] = metrics.record(api) {
    encryptedJson.secretRequestJson[RegistrationDownstreamResponse](
      Json.toJson(registration), { secretRequestJson =>
        http.POST(s"$serviceBaseUrl/developer", secretRequestJson, Seq(CONTENT_TYPE -> JSON)) map {
          r =>
            r.status match {
              case CREATED => RegistrationSuccessful
              case _ => throw new InternalServerException("Unexpected 2xx code")
            }
        } recover {
          case Upstream4xxResponse(_, CONFLICT, _, _) => EmailAlreadyInUse
        }
      })
  }

  def createUnregisteredUser(email: String)(implicit hc: HeaderCarrier): Future[Int] = metrics.record(api) {
    encryptedJson.secretRequestJson[Int](
      Json.toJson(UnregisteredUserCreationRequest(email)), { secretRequestJson =>
        http.POST(s"$serviceBaseUrl/unregistered-developer", secretRequestJson, Seq(CONTENT_TYPE -> JSON)) map status
      })
  }

  private def status: HttpResponse => Int = _.status

  def reset(reset: PasswordReset)(implicit hc: HeaderCarrier): Future[Int] = metrics.record(api) {
    encryptedJson.secretRequestJson[Int](
      Json.toJson(reset), { secretRequestJson =>
        http.POST(s"$serviceBaseUrl/reset-password", secretRequestJson, Seq(CONTENT_TYPE -> JSON))
          .map(status).recover {
          case Upstream4xxResponse(_, FORBIDDEN, _, _) => throw new UnverifiedAccount
        }
      })
  }

  def changePassword(change: ChangePassword)(implicit hc: HeaderCarrier): Future[Int] = metrics.record(api) {
    encryptedJson.secretRequestJson[Int](
      Json.toJson(change), { secretRequestJson =>
        http.POST(s"$serviceBaseUrl/change-password", secretRequestJson, Seq(CONTENT_TYPE -> JSON))
          .map(status).recover {
          case Upstream4xxResponse(_, UNAUTHORIZED, _, _) => throw new InvalidCredentials
          case Upstream4xxResponse(_, FORBIDDEN, _, _) => throw new UnverifiedAccount
          case Upstream4xxResponse(_, LOCKED, _, _) => throw new LockedAccount
        }
      })
  }

  import uk.gov.hmrc.http.HttpReads.Implicits._

  def requestReset(email: String)(implicit hc: HeaderCarrier): Future[Int] = metrics.record(api) {
    http.POSTEmpty[Either[UpstreamErrorResponse, HttpResponse]](s"$serviceBaseUrl/$email/password-reset-request", Seq((CONTENT_LENGTH -> "0")))
    .map(_ match {
      case Right(response) => response.status
      case Left(UpstreamErrorResponse(_,FORBIDDEN,_,_)) => throw new UnverifiedAccount
      case Left(err) => throw err
    })
  }

  def updateSessionLoggedInState(sessionId: String, request: UpdateLoggedInStateRequest)(implicit hc: HeaderCarrier): Future[Session] = metrics.record(api) {
    http.PUT[String, Option[Session]](s"$serviceBaseUrl/session/$sessionId/loggedInState/${request.loggedInState}", "")
      .map(_ match {
        case Some(session) => session
        case None => throw new SessionInvalid
      })
  }

  def fetchEmailForResetCode(code: String)(implicit hc: HeaderCarrier): Future[String] = {
    implicit val EmailForResetResponseReads = Json.reads[EmailForResetResponse]
    
    metrics.record(api) {
      http.GET[ErrorOr[EmailForResetResponse]](s"$serviceBaseUrl/reset-password?code=$code")
      .map {
        case Right(e) => e.email
        case Left(UpstreamErrorResponse(_,BAD_REQUEST,_,_)) => throw new InvalidResetCode
        case Left(UpstreamErrorResponse(_,FORBIDDEN,_,_)) => throw new UnverifiedAccount
        case Left(err) =>throw err
      }
    }
  }

  def updateProfile(email: String, profile: UpdateProfileRequest)(implicit hc: HeaderCarrier): Future[Int] = metrics.record(api) {
    http.POST[UpdateProfileRequest, ErrorOr[HttpResponse]](s"$serviceBaseUrl/developer/$email", profile)
      .map(_ match {
        case Right(response) => response.status
        case Left(err) => throw err
      })
  }

  def resendVerificationEmail(email: String)(implicit hc: HeaderCarrier): Future[Int] = metrics.record(api) {
    http.POSTEmpty[ErrorOr[HttpResponse]](s"$serviceBaseUrl/$email/resend-verification", Seq(CONTENT_LENGTH -> "0"))
      .map(_ match {
        case Right(response) => response.status
        case Left(err) => throw err
      })
  }

  def verify(code: String)(implicit hc: HeaderCarrier): Future[Int] = metrics.record(api) {
    http.GET[ErrorOr[HttpResponse]](s"$serviceBaseUrl/verification", Seq("code" -> code))
      .map(_ match {
        case Right(response) => response.status
        case Left(err) => throw err
      })
  }

  def fetchSession(sessionId: String)(implicit hc: HeaderCarrier): Future[Session] = metrics.record(api) {
    http.GET[Option[Session]](s"$serviceBaseUrl/session/$sessionId")
    .map(_ match {
      case Some(session) => session
      case None => throw new SessionInvalid
    })
  }

  def deleteSession(sessionId: String)(implicit hc: HeaderCarrier): Future[Int] = metrics.record(api) {
    http.DELETE[ErrorOr[HttpResponse]](s"$serviceBaseUrl/session/$sessionId")
      .map(_ match {
        case Right(response) => response.status
        // treat session not found as successfully destroyed
        case Left(UpstreamErrorResponse(_,NOT_FOUND,_,_)) => NO_CONTENT
        case Left(err) => throw err
      })
  }

  def updateRoles(email: String, roles: AccountSetupRequest)(implicit hc: HeaderCarrier): Future[Developer] =
    metrics.record(api) {
      http.PUT[AccountSetupRequest,Developer](s"$serviceBaseUrl/developer/account-setup/$email/roles", roles)
    }


  def updateServices(email: String, services: AccountSetupRequest)(implicit hc: HeaderCarrier): Future[Developer] =
    metrics.record(api) {
      http.PUT[AccountSetupRequest,Developer](s"$serviceBaseUrl/developer/account-setup/$email/services", services)
    }

  def updateTargets(email: String, targets: AccountSetupRequest)(implicit hc: HeaderCarrier): Future[Developer] =
    metrics.record(api) {
      http.PUT[AccountSetupRequest,Developer](s"$serviceBaseUrl/developer/account-setup/$email/targets", targets)
    }

  def completeAccountSetup(email: String)(implicit hc: HeaderCarrier): Future[Developer] =
    metrics.record(api) {
      http.POSTEmpty[Developer](s"$serviceBaseUrl/developer/account-setup/$email/complete", Seq((CONTENT_LENGTH -> "0")))
    }

  def fetchDeveloper(id: UserId)(implicit hc: HeaderCarrier): Future[Option[Developer]] = {
    metrics.record(api) {
      http.GET[Option[Developer]](s"$serviceBaseUrl/developer", Seq("developerId" -> id.asText))
    }
  }

  def fetchByEmails(emails: Set[String])(implicit hc: HeaderCarrier): Future[Seq[User]] = {
    http.GET[Seq[User]](s"$serviceBaseUrl/developers", Seq("emails" -> emails.mkString(",")))
  }

  def createMfaSecret(email: String)(implicit hc: HeaderCarrier): Future[String] = {
    implicit val CreateMfaResponseReads = Json.reads[CreateMfaResponse]

    metrics.record(api) {
      http.POSTEmpty[CreateMfaResponse](s"$serviceBaseUrl/developer/$email/mfa", Seq((CONTENT_LENGTH -> "0")))
      .map(_.secret)
    }
  }

  def verifyMfa(email: String, code: String)(implicit hc: HeaderCarrier): Future[Boolean] = {
    metrics.record(api) {
      http.POST[VerifyMfaRequest, ErrorOrUnit](s"$serviceBaseUrl/developer/$email/mfa/verification", VerifyMfaRequest(code))
      .map(_ match {
        case Right(()) => true
        case Left(UpstreamErrorResponse(_,BAD_REQUEST,_,_)) => false
        case Left(err) => throw err
      })
    }
  }

  def enableMfa(email: String)(implicit hc: HeaderCarrier): Future[Unit] = {
    metrics.record(api) {
      http.PUT[String, ErrorOrUnit](s"$serviceBaseUrl/developer/$email/mfa/enable", "")
      .map(throwOrUnit)
    }
  }

  def removeMfa(email: String)(implicit hc: HeaderCarrier): Future[Unit] = {
    implicit val RemoveMfaRequestFormat = Json.format[RemoveMfaRequest]
    metrics.record(api) {
      http.POST[RemoveMfaRequest, ErrorOrUnit](s"$serviceBaseUrl/developer/$email/mfa/remove", RemoveMfaRequest(email))
      .map(throwOrUnit)
    }
  }

  def removeEmailPreferences(emailAddress: String)(implicit hc: HeaderCarrier): Future[Boolean] = metrics.record(api) {
      http.DELETE[ErrorOrUnit](s"$serviceBaseUrl/developer/$emailAddress/email-preferences")
      .map(throwOrOptionOf)
      .map(_ match {
        case Some(_) => true
        case None => throw new InvalidEmail
      })
  }

  def updateEmailPreferences(emailAddress: String, emailPreferences: EmailPreferences)
      (implicit hc: HeaderCarrier): Future[Boolean] = metrics.record(api) {
    val url = s"$serviceBaseUrl/developer/$emailAddress/email-preferences"

    http.PUT[EmailPreferences, ErrorOrUnit](url, emailPreferences)
      .map(_ match {
        case Right(_) => true
        case Left(UpstreamErrorResponse(_,NOT_FOUND,_,_)) => throw new InvalidEmail
        case Left(err) => throw err
      })
  }
}

object ThirdPartyDeveloperConnector {
  private[connectors] case class UnregisteredUserCreationRequest(email: String)

  case class RemoveMfaRequest(removedBy: String)
  case class CreateMfaResponse(secret: String)
  case class EmailForResetResponse(email: String)
  object JsonFormatters {
    implicit val formatUnregisteredUserCreationRequest: Format[UnregisteredUserCreationRequest] = Json.format[UnregisteredUserCreationRequest]
  }
}
