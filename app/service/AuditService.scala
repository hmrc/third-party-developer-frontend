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

package service

import config.{ApplicationAuditConnector, ApplicationConfig}
import uk.gov.hmrc.play.audit.AuditExtensions.auditHeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.{AuditConnector, AuditResult}
import uk.gov.hmrc.play.audit.model.DataEvent

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import uk.gov.hmrc.http.HeaderCarrier

trait AuditService {
  val auditConnector: AuditConnector
  val applicationConfig: ApplicationConfig

  def audit(action: AuditAction, data: Map[String, String] = Map.empty)(implicit hc: HeaderCarrier): Future[AuditResult] =
    auditConnector.sendEvent(new DataEvent(
      auditSource = "third-party-developer-frontend",
      auditType = action.auditType,
      tags = Map("sandbox" -> s"{${applicationConfig.isExternalTestEnvironment}") ++
        hc.toAuditTags(action.name, "-") ++ userContext(hc) ++ action.tags.toSeq ++ data,
      detail = hc.toAuditDetails(action.details.toSeq: _*)
    ))

  private def userContext(hc: HeaderCarrier) =
    userContextFromHeaders(hc.headers.toMap)

  private def userContextFromHeaders(headers: Map[String, String]) = {

    def mapHeader(httpKey: String, auditKey: String) = headers.get(httpKey).map(auditKey -> _)

    val developerEmail = mapHeader("X-email-address", "developerEmail")
    val developerFullName = mapHeader("X-name", "developerFullName")

    Seq(developerEmail, developerFullName).flatten.toMap
  }
}

object AuditService extends AuditService {
  override val auditConnector = ApplicationAuditConnector
  override val applicationConfig = ApplicationConfig
}

sealed trait AuditAction {
  val auditType: String
  val name: String
  val tags: Map[String, String] = Map.empty
  val details: Map[String, String] = Map.empty
}

object AuditAction {

  case class ApplicationUpliftRequestDeniedDueToInvalidCredentials(applicationId: String) extends AuditAction {
    val name = "Application uplift to production request has been denied, due to invalid credentials"
    val auditType = "ApplicationUpliftRequestDeniedDueToInvalidCredentials"
    override val details = Map(
      "applicationId" -> applicationId
    )
  }

  case class PasswordChangeFailedDueToInvalidCredentials(email: String) extends AuditAction {
    val name = "Password change request has been denied, due to invalid credentials"
    val auditType = "PasswordChangeFailedDueToInvalidCredentials"
    override val tags = Map(
      "developerEmail" -> email
    )
  }

  case object LoginSucceeded extends AuditAction {
    override val name: String = "Login successful"
    override val auditType: String = "LoginSucceeded"
  }

  case object LoginFailedDueToInvalidEmail extends AuditAction {
    val name = "Login failed due to invalid email address"
    val auditType = "LoginFailedDueToInvalidEmail"
  }

  case object LoginFailedDueToInvalidPassword extends AuditAction {
    val name = "Login failed due to invalid password"
    val auditType = "LoginFailedDueToInvalidPassword"
  }

  case object LoginFailedDueToLockedAccount extends AuditAction {
    val name = "Login failed due to locked account"
    val auditType = "LoginFailedDueToLockedAccount"
  }

  case object AccountDeletionRequested extends AuditAction {
    val name = "Developer has requested an account deletion"
    val auditType = "AccountDeletionRequested"
  }

  case object ApplicationDeletionRequested extends AuditAction {
    override val name: String = "Developer has requested application deletion"
    override val auditType: String = "ApplicationDeletionRequest"
  }
}
