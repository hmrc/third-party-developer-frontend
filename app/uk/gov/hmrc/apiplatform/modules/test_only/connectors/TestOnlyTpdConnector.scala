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

package uk.gov.hmrc.apiplatform.modules.test_only.connectors

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

import play.api.libs.json._
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, StringContextOps}

import uk.gov.hmrc.apiplatform.modules.common.domain.models.{LaxEmailAddress, UserId}
import uk.gov.hmrc.apiplatform.modules.tpd.core.domain.models.User
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ApplicationConfig

object TestOnlyTpdConnector {
  case class CloneUserResponse(userId: UserId, emailAddress: LaxEmailAddress)
  case class SmsAccessCodeResponse(accessCode: String)

  implicit val formatCloneUserResponse: OFormat[CloneUserResponse]         = Json.format[CloneUserResponse]
  implicit val formatSmsAccessCodeResponse: OFormat[SmsAccessCodeResponse] = Json.format[SmsAccessCodeResponse]
}

@Singleton
class TestOnlyTpdConnector @Inject() (
    http: HttpClientV2,
    config: ApplicationConfig
  )(implicit val ec: ExecutionContext
  ) {

  import TestOnlyTpdConnector._

  lazy val serviceBaseUrl: String = config.thirdPartyDeveloperUrl

  def clone(email: LaxEmailAddress)(implicit hc: HeaderCarrier): Future[CloneUserResponse] = {
    http.post(url"$serviceBaseUrl/test-only/user/clone")
      .withBody(Json.toJson(email))
      .execute[CloneUserResponse]
  }

  def findUserByEmail(email: LaxEmailAddress)(implicit hc: HeaderCarrier): Future[Option[User]] = {
    http.post(url"$serviceBaseUrl/test-only/user/find")
      .withBody(Json.toJson(email))
      .execute[Option[User]]
  }

  def peekAtPasswordResetCode(email: LaxEmailAddress)(implicit hc: HeaderCarrier): Future[Option[String]] = {
    http.post(url"$serviceBaseUrl/test-only/user/peekAtPasswordResetCode")
      .withBody(Json.toJson(email))
      .execute[Option[String]]
  }

  def peekAtRegistrationVerificationCode(email: LaxEmailAddress)(implicit hc: HeaderCarrier): Future[Option[String]] = {
    http.post(url"$serviceBaseUrl/test-only/user/peekAtRegistrationVerificationCode")
      .withBody(Json.toJson(email))
      .execute[Option[String]]
  }

  def peekAtSmsAccessCode(email: LaxEmailAddress)(implicit hc: HeaderCarrier): Future[Option[String]] = {
    http.post(url"$serviceBaseUrl/test-only/user/peekAtSmsAccessCode")
      .withBody(Json.toJson(email))
      .execute[Option[String]]
  }
}
