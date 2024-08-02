/*
 * Copyright 2023 HM Revenue & Customs
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

package uk.gov.hmrc.thirdpartydeveloperfrontend.connectors

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

import play.api.Logging
import play.api.http.Status._
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.{HttpClient, SessionId => _, _}
import uk.gov.hmrc.play.http.metrics.common.API

import uk.gov.hmrc.apiplatform.modules.common.domain.models.{LaxEmailAddress, UserId}
import uk.gov.hmrc.apiplatform.modules.tpd.core.domain.models.{SessionId, User}
import uk.gov.hmrc.apiplatform.modules.tpd.core.dto._
import uk.gov.hmrc.apiplatform.modules.tpd.domain.models._
import uk.gov.hmrc.apiplatform.modules.tpd.emailpreferences.domain.models.EmailPreferences
import uk.gov.hmrc.apiplatform.modules.tpd.mfa.dto.AccessCodeAuthenticationRequest
import uk.gov.hmrc.apiplatform.modules.tpd.session.domain.models._
import uk.gov.hmrc.apiplatform.modules.tpd.session.dto._
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ApplicationConfig
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors._

object ThirdPartyDeveloperConnector {
  case class CoreUserDetails(email: LaxEmailAddress, id: UserId)
}

@Singleton
class ThirdPartyDeveloperConnector @Inject() (
    http: HttpClient,
    encryptedJson: EncryptedJson,
    config: ApplicationConfig,
    metrics: ConnectorMetrics
  )(implicit val ec: ExecutionContext
  ) extends CommonResponseHandlers with Logging {

  import ThirdPartyDeveloperConnector._

  def authenticate(loginRequest: SessionCreateWithDeviceRequest)(implicit hc: HeaderCarrier): Future[UserAuthenticationResponse] = metrics.record(api) {
    encryptedJson.secretRequest(
      loginRequest,
      http.POST[SecretRequest, ErrorOr[UserAuthenticationResponse]](s"$serviceBaseUrl/authenticate", _)
    )
      .map {
        case Right(response)                                    => response
        case Left(UpstreamErrorResponse(_, UNAUTHORIZED, _, _)) => throw new InvalidCredentials
        case Left(UpstreamErrorResponse(_, FORBIDDEN, _, _))    => throw new UnverifiedAccount
        case Left(UpstreamErrorResponse(_, LOCKED, _, _))       => throw new LockedAccount
        case Left(UpstreamErrorResponse(_, NOT_FOUND, _, _))    => throw new InvalidEmail
        case Left(err)                                          => throw err
      }
  }

  def authenticateMfaAccessCode(
      accessCodeAuthenticationRequest: AccessCodeAuthenticationRequest
    )(implicit hc: HeaderCarrier
    ): Future[UserSession] = metrics.record(api) {

    encryptedJson.secretRequest(
      accessCodeAuthenticationRequest,
      http.POST[SecretRequest, ErrorOr[UserSession]](s"$serviceBaseUrl/authenticate-mfa", _)
    )
      .map {
        case Right(response)                                   => response
        case Left(UpstreamErrorResponse(_, BAD_REQUEST, _, _)) => throw new InvalidCredentials
        case Left(UpstreamErrorResponse(_, NOT_FOUND, _, _))   => throw new InvalidEmail
        case Left(err)                                         => throw err
      }
  }

  lazy val serviceBaseUrl: String = config.thirdPartyDeveloperUrl
  val api: API                    = API("third-party-developer")

  def register(registration: Registration)(implicit hc: HeaderCarrier): Future[RegistrationDownstreamResponse] = metrics.record(api) {
    encryptedJson.secretRequest(
      registration,
      http.POST[SecretRequest, ErrorOr[HttpResponse]](s"$serviceBaseUrl/developer", _)
        .map {
          case Right(response) if (response.status == CREATED) => RegistrationSuccessful
          case Right(response)                                 => throw new InternalServerException("Unexpected 2xx code")
          case Left(UpstreamErrorResponse(_, CONFLICT, _, _))  => EmailAlreadyInUse
          case Left(err)                                       => throw err
        }
    )
  }

  def createUnregisteredUser(email: LaxEmailAddress)(implicit hc: HeaderCarrier): Future[Int] = metrics.record(api) {
    encryptedJson.secretRequest(
      UnregisteredUserCreationRequest(email),
      http.POST[SecretRequest, ErrorOr[HttpResponse]](s"$serviceBaseUrl/unregistered-developer", _)
        .map {
          case Right(response) => response.status
          case Left(err)       => throw err
        }
    )
  }

  def reset(reset: PasswordResetRequest)(implicit hc: HeaderCarrier): Future[Int] = metrics.record(api) {
    encryptedJson.secretRequest(
      reset,
      http.POST[SecretRequest, ErrorOr[HttpResponse]](s"$serviceBaseUrl/reset-password", _)
        .map {
          case Right(response)                                 => response.status
          case Left(UpstreamErrorResponse(_, FORBIDDEN, _, _)) => throw new UnverifiedAccount
          case Left(err)                                       => throw err
        }
    )
  }

  def changePassword(change: PasswordChangeRequest)(implicit hc: HeaderCarrier): Future[Int] = metrics.record(api) {
    encryptedJson.secretRequest(
      change,
      http.POST[SecretRequest, ErrorOr[HttpResponse]](s"$serviceBaseUrl/change-password", _)
        .map {
          case Right(response)                                    => response.status
          case Left(UpstreamErrorResponse(_, UNAUTHORIZED, _, _)) => throw new InvalidCredentials
          case Left(UpstreamErrorResponse(_, FORBIDDEN, _, _))    => throw new UnverifiedAccount
          case Left(UpstreamErrorResponse(_, LOCKED, _, _))       => throw new LockedAccount
          case Left(err)                                          => throw err
        }
    )
  }

  def requestReset(email: LaxEmailAddress)(implicit hc: HeaderCarrier): Future[Int] = metrics.record(api) {
    http.POST[EmailIdentifier, Either[UpstreamErrorResponse, HttpResponse]](s"$serviceBaseUrl/password-reset-request", EmailIdentifier(email))
      .map {
        case Right(response)                                 => response.status
        case Left(UpstreamErrorResponse(_, FORBIDDEN, _, _)) => throw new UnverifiedAccount
        case Left(err)                                       => throw err
      }
  }

  def updateSessionLoggedInState(sessionId: SessionId, request: UpdateLoggedInStateRequest)(implicit hc: HeaderCarrier): Future[UserSession] = metrics.record(api) {
    http.PUT[String, Option[UserSession]](s"$serviceBaseUrl/session/$sessionId/loggedInState/${request.loggedInState}", "")
      .map {
        case Some(session) => session
        case None          => throw new SessionInvalid
      }
  }

  def fetchEmailForResetCode(code: String)(implicit hc: HeaderCarrier): Future[LaxEmailAddress] = {
    metrics.record(api) {
      http.GET[ErrorOr[EmailIdentifier]](s"$serviceBaseUrl/reset-password?code=$code")
        .map {
          case Right(e)                                          => e.email
          case Left(UpstreamErrorResponse(_, BAD_REQUEST, _, _)) => throw new InvalidResetCode
          case Left(UpstreamErrorResponse(_, FORBIDDEN, _, _))   => throw new UnverifiedAccount
          case Left(err)                                         => throw err
        }
    }
  }

  def updateProfile(userId: UserId, profile: UpdateProfileRequest)(implicit hc: HeaderCarrier): Future[Int] = metrics.record(api) {
    http.POST[UpdateProfileRequest, ErrorOr[HttpResponse]](s"$serviceBaseUrl/developer/$userId", profile)
      .map {
        case Right(response) => response.status
        case Left(err)       => throw err
      }
  }

  def findUserId(email: LaxEmailAddress)(implicit hc: HeaderCarrier): Future[Option[CoreUserDetails]] = {
    http.POST[FindUserIdRequest, Option[FindUserIdResponse]](s"$serviceBaseUrl/developers/find-user-id", FindUserIdRequest(email))
      .map {
        case Some(response) => Some(CoreUserDetails(email, response.userId))
        case None           => None
      }
  }

  def fetchUserId(email: LaxEmailAddress)(implicit hc: HeaderCarrier): Future[CoreUserDetails] = {
    http.POST[FindUserIdRequest, FindUserIdResponse](s"$serviceBaseUrl/developers/find-user-id", FindUserIdRequest(email))
      .map(response => CoreUserDetails(email, response.userId))
  }

  def resendVerificationEmail(email: LaxEmailAddress)(implicit hc: HeaderCarrier): Future[Int] = metrics.record(api) {
    for {
      coreUserDetails <- fetchUserId(email)
      userId           = coreUserDetails.id.value
      response        <- http.POSTEmpty[ErrorOr[HttpResponse]](s"$serviceBaseUrl/$userId/resend-verification")
                           .map {
                             case Right(response) => response.status
                             case Left(err)       => throw err
                           }
    } yield response
  }

  def verify(code: String)(implicit hc: HeaderCarrier): Future[Int] = metrics.record(api) {
    http.GET[ErrorOr[HttpResponse]](s"$serviceBaseUrl/verification", Seq("code" -> code))
      .map {
        case Right(response) => response.status
        case Left(err)       => throw err
      }
  }

  def fetchSession(sessionId: SessionId)(implicit hc: HeaderCarrier): Future[UserSession] = metrics.record(api) {
    http.GET[Option[UserSession]](s"$serviceBaseUrl/session/$sessionId")
      .map {
        case Some(session) => session
        case None          => throw new SessionInvalid
      }
  }

  def deleteSession(sessionId: SessionId)(implicit hc: HeaderCarrier): Future[Int] = metrics.record(api) {
    http.DELETE[ErrorOr[HttpResponse]](s"$serviceBaseUrl/session/$sessionId")
      .map {
        case Right(response)                                 => response.status
        // treat session not found as successfully destroyed
        case Left(UpstreamErrorResponse(_, NOT_FOUND, _, _)) => NO_CONTENT
        case Left(err)                                       => throw err
      }
  }

  def updateRoles(userId: UserId, roles: AccountSetupRequest)(implicit hc: HeaderCarrier): Future[User] =
    metrics.record(api) {
      http.PUT[AccountSetupRequest, User](s"$serviceBaseUrl/developer/account-setup/$userId/roles", roles)
    }

  def updateServices(userId: UserId, services: AccountSetupRequest)(implicit hc: HeaderCarrier): Future[User] =
    metrics.record(api) {
      http.PUT[AccountSetupRequest, User](s"$serviceBaseUrl/developer/account-setup/$userId/services", services)
    }

  def updateTargets(userId: UserId, targets: AccountSetupRequest)(implicit hc: HeaderCarrier): Future[User] =
    metrics.record(api) {
      http.PUT[AccountSetupRequest, User](s"$serviceBaseUrl/developer/account-setup/$userId/targets", targets)
    }

  def completeAccountSetup(userId: UserId)(implicit hc: HeaderCarrier): Future[User] =
    metrics.record(api) {
      http.POSTEmpty[User](s"$serviceBaseUrl/developer/account-setup/$userId/complete")
    }

  def fetchDeveloper(id: UserId)(implicit hc: HeaderCarrier): Future[Option[User]] = {
    metrics.record(api) {
      http.GET[Option[User]](s"$serviceBaseUrl/developer", Seq("developerId" -> id.toString()))
    }
  }

  def fetchByEmails(emails: Set[LaxEmailAddress])(implicit hc: HeaderCarrier): Future[Seq[User]] = {
    http.POST[Set[LaxEmailAddress], Seq[User]](s"$serviceBaseUrl/developers/get-by-emails", emails)
  }

  def removeEmailPreferences(userId: UserId)(implicit hc: HeaderCarrier): Future[Boolean] = metrics.record(api) {
    http.DELETE[ErrorOrUnit](s"$serviceBaseUrl/developer/$userId/email-preferences")
      .map(throwOrOptionOf)
      .map {
        case Some(_) => true
        case None    => throw new InvalidEmail
      }
  }

  def updateEmailPreferences(userId: UserId, emailPreferences: EmailPreferences)(implicit hc: HeaderCarrier): Future[Boolean] = metrics.record(api) {
    val url = s"$serviceBaseUrl/developer/$userId/email-preferences"

    http.PUT[EmailPreferences, ErrorOrUnit](url, emailPreferences)
      .map {
        case Right(_)                                        => true
        case Left(UpstreamErrorResponse(_, NOT_FOUND, _, _)) => throw new InvalidEmail
        case Left(err)                                       => throw err
      }
  }

  def getOrCreateUserId(emailAddress: LaxEmailAddress)(implicit hc: HeaderCarrier): Future[UserId] = {
    http.POST[FindOrCreateUserIdRequest, FindUserIdResponse](s"$serviceBaseUrl/developers/user-id", FindOrCreateUserIdRequest(emailAddress)).map(_.userId)
  }
}
