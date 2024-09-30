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
import play.api.libs.json.Json
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{SessionId => _, _}
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

object ThirdPartyDeveloperConnector {
  case class CoreUserDetails(email: LaxEmailAddress, id: UserId)
}

@Singleton
class ThirdPartyDeveloperConnector @Inject() (
    http: HttpClientV2,
    encryptedJson: EncryptedJson,
    config: ApplicationConfig,
    metrics: ConnectorMetrics
  )(implicit val ec: ExecutionContext
  ) extends CommonResponseHandlers with Logging {

  import ThirdPartyDeveloperConnector._

  def authenticate(loginRequest: SessionCreateWithDeviceRequest)(implicit hc: HeaderCarrier): Future[UserAuthenticationResponse] = metrics.record(api) {
    encryptedJson.secretRequest(
      loginRequest,
      encrypted =>
        http.post(url"$serviceBaseUrl/authenticate")
          .withBody(Json.toJson(encrypted))
          .execute[ErrorOr[UserAuthenticationResponse]]
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
      encrypted =>
        http
          .post(url"$serviceBaseUrl/authenticate-mfa")
          .withBody(Json.toJson(encrypted))
          .execute[ErrorOr[UserSession]]
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

  def register(registration: RegistrationRequest)(implicit hc: HeaderCarrier): Future[RegistrationDownstreamResponse] = metrics.record(api) {
    encryptedJson.secretRequest(
      registration,
      encrypted =>
        http.post(url"$serviceBaseUrl/developer")
          .withBody(Json.toJson(encrypted))
          .execute[ErrorOr[HttpResponse]]
    )
      .map {
        case Right(response) if (response.status == CREATED) => RegistrationSuccessful
        case Right(response)                                 => throw new InternalServerException("Unexpected 2xx code")
        case Left(UpstreamErrorResponse(_, CONFLICT, _, _))  => EmailAlreadyInUse
        case Left(err)                                       => throw err
      }
  }

  def createUnregisteredUser(email: LaxEmailAddress)(implicit hc: HeaderCarrier): Future[Int] = metrics.record(api) {
    encryptedJson.secretRequest(
      UnregisteredUserCreationRequest(email),
      encrypted =>
        http.post(url"$serviceBaseUrl/unregistered-developer")
          .withBody(Json.toJson(encrypted))
          .execute[ErrorOr[HttpResponse]]
    )
      .map {
        case Right(response) => response.status
        case Left(err)       => throw err
      }
  }

  def reset(reset: PasswordResetRequest)(implicit hc: HeaderCarrier): Future[Int] = metrics.record(api) {
    encryptedJson.secretRequest(
      reset,
      encrypted =>
        http.post(url"$serviceBaseUrl/reset-password")
          .withBody(Json.toJson(encrypted))
          .execute[ErrorOr[HttpResponse]]
    )
      .map {
        case Right(response)                                 => response.status
        case Left(UpstreamErrorResponse(_, FORBIDDEN, _, _)) => throw new UnverifiedAccount
        case Left(err)                                       => throw err
      }
  }

  def changePassword(change: PasswordChangeRequest)(implicit hc: HeaderCarrier): Future[Int] = metrics.record(api) {
    encryptedJson.secretRequest(
      change,
      encrypted =>
        http.post(url"$serviceBaseUrl/change-password")
          .withBody(Json.toJson(encrypted))
          .execute[ErrorOr[HttpResponse]]
    )
      .map {
        case Right(response)                                    => response.status
        case Left(UpstreamErrorResponse(_, UNAUTHORIZED, _, _)) => throw new InvalidCredentials
        case Left(UpstreamErrorResponse(_, FORBIDDEN, _, _))    => throw new UnverifiedAccount
        case Left(UpstreamErrorResponse(_, LOCKED, _, _))       => throw new LockedAccount
        case Left(err)                                          => throw err
      }
  }

  def requestReset(email: LaxEmailAddress)(implicit hc: HeaderCarrier): Future[Int] = metrics.record(api) {
    http.post(url"$serviceBaseUrl/password-reset-request")
      .withBody(Json.toJson(EmailIdentifier(email)))
      .execute[Either[UpstreamErrorResponse, HttpResponse]]
      .map {
        case Right(response)                                 => response.status
        case Left(UpstreamErrorResponse(_, FORBIDDEN, _, _)) => throw new UnverifiedAccount
        case Left(err)                                       => throw err
      }
  }

  def updateSessionLoggedInState(sessionId: SessionId, request: UpdateLoggedInStateRequest)(implicit hc: HeaderCarrier): Future[UserSession] = metrics.record(api) {
    http.put(url"$serviceBaseUrl/session/$sessionId/loggedInState/${request.loggedInState}")
      .execute[Option[UserSession]]
      .map {
        case Some(session) => session
        case None          => throw new SessionInvalid
      }
  }

  def fetchEmailForResetCode(code: String)(implicit hc: HeaderCarrier): Future[LaxEmailAddress] = {
    metrics.record(api) {
      http
        .get(url"$serviceBaseUrl/reset-password?code=$code")
        .execute[ErrorOr[EmailIdentifier]]
        .map {
          case Right(e)                                          => e.email
          case Left(UpstreamErrorResponse(_, BAD_REQUEST, _, _)) => throw new InvalidResetCode
          case Left(UpstreamErrorResponse(_, FORBIDDEN, _, _))   => throw new UnverifiedAccount
          case Left(err)                                         => throw err
        }
    }
  }

  def updateProfile(userId: UserId, profile: UpdateRequest)(implicit hc: HeaderCarrier): Future[Int] = metrics.record(api) {
    http.post(url"$serviceBaseUrl/developer/$userId")
      .withBody(Json.toJson(profile))
      .execute[ErrorOr[HttpResponse]]
      .map {
        case Right(response) => response.status
        case Left(err)       => throw err
      }
  }

  def findUserId(email: LaxEmailAddress)(implicit hc: HeaderCarrier): Future[Option[CoreUserDetails]] = {
    http.post(url"$serviceBaseUrl/developers/find-user-id")
      .withBody(Json.toJson(FindUserIdRequest(email)))
      .execute[Option[FindUserIdResponse]]
      .map {
        case Some(response) => Some(CoreUserDetails(email, response.userId))
        case None           => None
      }
  }

  def fetchUserId(email: LaxEmailAddress)(implicit hc: HeaderCarrier): Future[CoreUserDetails] = {
    http
      .post(url"$serviceBaseUrl/developers/find-user-id")
      .withBody(Json.toJson(FindUserIdRequest(email)))
      .execute[FindUserIdResponse]
      .map(response => CoreUserDetails(email, response.userId))
  }

  def resendVerificationEmail(email: LaxEmailAddress)(implicit hc: HeaderCarrier): Future[Int] = metrics.record(api) {
    for {
      coreUserDetails <- fetchUserId(email)
      userId           = coreUserDetails.id.value
      response        <- http
                           .post(url"$serviceBaseUrl/$userId/resend-verification")
                           .execute[ErrorOr[HttpResponse]]
                           .map {
                             case Right(response) => response.status
                             case Left(err)       => throw err
                           }
    } yield response
  }

  def verify(code: String)(implicit hc: HeaderCarrier): Future[Int] = metrics.record(api) {
    http.get(url"$serviceBaseUrl/verification?code=$code")
      .execute[ErrorOr[HttpResponse]]
      .map {
        case Right(response) => response.status
        case Left(err)       => throw err
      }
  }

  def fetchSession(sessionId: SessionId)(implicit hc: HeaderCarrier): Future[UserSession] = metrics.record(api) {
    http.get(url"$serviceBaseUrl/session/$sessionId")
      .execute[Option[UserSession]]
      .map {
        case Some(session) => session
        case None          => throw new SessionInvalid
      }
  }

  def deleteSession(sessionId: SessionId)(implicit hc: HeaderCarrier): Future[Int] = metrics.record(api) {
    http.delete(url"$serviceBaseUrl/session/$sessionId")
      .execute[ErrorOr[HttpResponse]]
      .map {
        case Right(response)                                 => response.status
        // treat session not found as successfully destroyed
        case Left(UpstreamErrorResponse(_, NOT_FOUND, _, _)) => NO_CONTENT
        case Left(err)                                       => throw err
      }
  }

  def updateRoles(userId: UserId, roles: AccountSetupRequest)(implicit hc: HeaderCarrier): Future[User] =
    metrics.record(api) {
      http.put(url"$serviceBaseUrl/developer/account-setup/$userId/roles")
        .withBody(Json.toJson(roles))
        .execute[User]
    }

  def updateServices(userId: UserId, services: AccountSetupRequest)(implicit hc: HeaderCarrier): Future[User] =
    metrics.record(api) {
      http
        .put(url"$serviceBaseUrl/developer/account-setup/$userId/services")
        .withBody(Json.toJson(services))
        .execute[User]
    }

  def updateTargets(userId: UserId, targets: AccountSetupRequest)(implicit hc: HeaderCarrier): Future[User] =
    metrics.record(api) {
      http
        .put(url"$serviceBaseUrl/developer/account-setup/$userId/targets")
        .withBody(Json.toJson(targets))
        .execute[User]
    }

  def completeAccountSetup(userId: UserId)(implicit hc: HeaderCarrier): Future[User] =
    metrics.record(api) {
      http
        .post(url"$serviceBaseUrl/developer/account-setup/$userId/complete")
        .execute[User]
    }

  def fetchDeveloper(id: UserId)(implicit hc: HeaderCarrier): Future[Option[User]] = {
    metrics.record(api) {
      http
        .get(url"$serviceBaseUrl/developer?${Seq("developerId" -> id.toString())}")
        .execute[Option[User]]
    }
  }

  def fetchByEmails(emails: Set[LaxEmailAddress])(implicit hc: HeaderCarrier): Future[Seq[User]] = {
    http
      .post(url"$serviceBaseUrl/developers/get-by-emails")
      .withBody(Json.toJson(emails))
      .execute[Seq[User]]
  }

  def removeEmailPreferences(userId: UserId)(implicit hc: HeaderCarrier): Future[Boolean] = metrics.record(api) {
    http
      .delete(url"$serviceBaseUrl/developer/$userId/email-preferences")
      .execute[ErrorOrUnit]
      .map(throwOrOptionOf)
      .map {
        case Some(_) => true
        case None    => throw new InvalidEmail
      }
  }

  def updateEmailPreferences(userId: UserId, emailPreferences: EmailPreferences)(implicit hc: HeaderCarrier): Future[Boolean] = metrics.record(api) {
    val url = s"$serviceBaseUrl/developer/$userId/email-preferences"

    http
      .put(url"$url")
      .withBody(Json.toJson(emailPreferences))
      .execute[ErrorOrUnit]
      .map {
        case Right(_)                                        => true
        case Left(UpstreamErrorResponse(_, NOT_FOUND, _, _)) => throw new InvalidEmail
        case Left(err)                                       => throw err
      }
  }

  def getOrCreateUserId(emailAddress: LaxEmailAddress)(implicit hc: HeaderCarrier): Future[UserId] = {
    http
      .post(url"$serviceBaseUrl/developers/user-id")
      .withBody(Json.toJson(FindOrCreateUserIdRequest(emailAddress)))
      .execute[FindUserIdResponse]
      .map(_.userId)
  }
}
