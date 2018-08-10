/*
 * Copyright 2018 HM Revenue & Customs
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

import config.{ApplicationConfig, WSHttp}
import domain._
import play.api.http.Status
import play.api.http.ContentTypes.JSON
import play.api.http.HeaderNames.CONTENT_TYPE
import play.api.libs.json.Json
import uk.gov.hmrc.play.http.metrics.{API, Metrics, PlayMetrics}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import uk.gov.hmrc.http.{BadRequestException, HeaderCarrier, HttpResponse, InternalServerException, NotFoundException, Upstream4xxResponse}

trait ThirdPartyDeveloperConnector extends EncryptedJson {
  val serviceBaseUrl: String
  val http: WSHttp

  val metrics: Metrics
  val api = API("third-party-developer")

  def register(registration: Registration)(implicit hc: HeaderCarrier): Future[RegistrationDownstreamResponse] = metrics.record(api) {
    secretRequestJson[RegistrationDownstreamResponse](
      Json.toJson(registration), { secretRequestJson =>
        http.POST(s"$serviceBaseUrl/developer", secretRequestJson, Seq(CONTENT_TYPE -> JSON)) map {
          r =>
            r.status match {
              case Status.CREATED => RegistrationSuccessful
              case _ => throw new InternalServerException("Unexpected 2xx code")
            }
        } recover {
          case Upstream4xxResponse(_, Status.CONFLICT, _, _) => EmailAlreadyInUse
        }
      })
  }

  def updateProfile(email: String, profile: UpdateProfileRequest)(implicit hc: HeaderCarrier): Future[Int] = metrics.record(api) {
    http.POST(s"$serviceBaseUrl/developer/$email", profile) map status
  }

  def resendVerificationEmail(email: String)(implicit hc: HeaderCarrier): Future[Int] = metrics.record(api) {
    http.POSTEmpty(s"$serviceBaseUrl/$email/resend-verification") map status
  }

  def verify(code: String)(implicit hc: HeaderCarrier): Future[Int] = metrics.record(api) {
    http.GET(s"$serviceBaseUrl/verification?code=$code") map status
  }

  private def status: (HttpResponse) => Int = _.status

  def fetchEmailForResetCode(code: String)(implicit hc: HeaderCarrier): Future[String] = metrics.record(api) {
    http.GET(s"$serviceBaseUrl/reset-password?code=$code")
      .map(r => (r.json \ "email").as[String])
      .recover {
      case _ : BadRequestException => throw new InvalidResetCode
      case Upstream4xxResponse(_, Status.FORBIDDEN, _, _) => throw new UnverifiedAccount
    }
  }

  def requestReset(email: String)(implicit hc: HeaderCarrier): Future[Int] = metrics.record(api) {
    http.POSTEmpty(s"$serviceBaseUrl/$email/password-reset-request").map(status).recover {
      case Upstream4xxResponse(_, Status.FORBIDDEN, _, _) => throw new UnverifiedAccount
    }
  }

  def reset(reset: PasswordReset)(implicit hc: HeaderCarrier): Future[Int] = metrics.record(api) {
    secretRequestJson[Int](
      Json.toJson(reset), { secretRequestJson =>
        http.POST(s"$serviceBaseUrl/reset-password", secretRequestJson, Seq(CONTENT_TYPE -> JSON))
          .map(status).recover {
          case Upstream4xxResponse(_, Status.FORBIDDEN, _, _) => throw new UnverifiedAccount
        }
      })
  }

  def changePassword(change: ChangePassword)(implicit hc: HeaderCarrier): Future[Int] = metrics.record(api) { secretRequestJson[Int](
    Json.toJson(change), { secretRequestJson =>
      http.POST(s"$serviceBaseUrl/change-password", secretRequestJson, Seq(CONTENT_TYPE -> JSON))
        .map(status).recover {
        case Upstream4xxResponse(_, Status.UNAUTHORIZED, _, _) => throw new InvalidCredentials
        case Upstream4xxResponse(_, Status.FORBIDDEN, _, _) => throw new UnverifiedAccount
        case Upstream4xxResponse(_, Status.LOCKED, _, _) => throw new LockedAccount
      }
    })
  }

  def createSession(loginRequest: LoginRequest)(implicit hc: HeaderCarrier): Future[Session] = metrics.record(api) {
    secretRequestJson(
      Json.toJson(loginRequest),
      http.POST(s"$serviceBaseUrl/session", _, Seq(CONTENT_TYPE -> JSON)))
      .map(_.json.as[Session])
      .recover {
        case Upstream4xxResponse(_, Status.UNAUTHORIZED, _, _) => throw new InvalidCredentials
        case Upstream4xxResponse(_, Status.FORBIDDEN, _, _) => throw new UnverifiedAccount
        case Upstream4xxResponse(_, Status.LOCKED, _, _) => throw new LockedAccount
        case _: NotFoundException => throw new InvalidEmail
      }
  }

  def fetchSession(sessionId: String)(implicit hc: HeaderCarrier): Future[Session] = metrics.record(api) {
    http.GET(s"$serviceBaseUrl/session/$sessionId")
      .map(_.json.as[Session])
      .recover {
        case _: NotFoundException => throw new SessionInvalid
      }
  }

  def deleteSession(sessionId: String)(implicit hc: HeaderCarrier): Future[Int] = metrics.record(api) {
    http.DELETE(s"$serviceBaseUrl/session/$sessionId")
      .map(status)
      .recover {
        // treat session not found as successfully destroyed
        case _: NotFoundException => Status.NO_CONTENT
      }
  }

  def checkPassword(checkRequest: PasswordCheckRequest)(implicit hc: HeaderCarrier): Future[VerifyPasswordSuccessful] = metrics.record(api) {
    secretRequestJson(
      Json.toJson(checkRequest),
      http.POST(s"$serviceBaseUrl/check-password", _, Seq(CONTENT_TYPE -> JSON)))
      .map(_ => VerifyPasswordSuccessful)
      .recover {
        case Upstream4xxResponse(_, Status.UNAUTHORIZED, _, _) => throw new InvalidCredentials
        case Upstream4xxResponse(_, Status.FORBIDDEN, _, _) => throw new UnverifiedAccount
        case Upstream4xxResponse(_, Status.LOCKED, _, _) => throw new LockedAccount
      }
  }

  def updateRoles(email: String, roles: AccountSetupRequest)(implicit hc: HeaderCarrier): Future[Developer] =
    metrics.record(api) {
      http.PUT(s"$serviceBaseUrl/developer/account-setup/$email/roles", roles)
        .map(_.json.as[Developer])
    }


  def updateServices(email: String, services: AccountSetupRequest)(implicit hc: HeaderCarrier): Future[Developer] =
    metrics.record(api) {
      http.PUT(s"$serviceBaseUrl/developer/account-setup/$email/services", services)
        .map(_.json.as[Developer])
    }

  def updateTargets(email: String, targets: AccountSetupRequest)(implicit hc: HeaderCarrier): Future[Developer] =
    metrics.record(api) {
      http.PUT(s"$serviceBaseUrl/developer/account-setup/$email/targets", targets)
        .map(_.json.as[Developer])
    }

  def completeAccountSetup(email: String)(implicit hc: HeaderCarrier): Future[Developer] =
    metrics.record(api) {
      http.POSTEmpty(s"$serviceBaseUrl/developer/account-setup/$email/complete")
        .map(_.json.as[Developer])
    }


  def fetchDeveloper(email: String)(implicit hc: HeaderCarrier): Future[Option[Developer]] = {
    metrics.record(api) {
      http.GET[Developer](s"$serviceBaseUrl/developer", Seq("email" -> email)) map { result =>
        Option(result)
      } recover {
        case _: NotFoundException => None
      }
    }
  }

  def fetchByEmails(emails: Set[String])(implicit hc: HeaderCarrier) = {
    http.GET[Seq[User]](s"$serviceBaseUrl/developers", Seq("emails" -> emails.mkString(",")))
  }
}

object ThirdPartyDeveloperConnector extends ThirdPartyDeveloperConnector {
  val serviceBaseUrl = ApplicationConfig.thirdPartyDeveloperUrl
  val http = WSHttp
  val payloadEncryption = PayloadEncryption
  val metrics = PlayMetrics
}
