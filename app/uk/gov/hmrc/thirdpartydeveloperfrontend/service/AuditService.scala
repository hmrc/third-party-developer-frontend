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

package uk.gov.hmrc.thirdpartydeveloperfrontend.service

import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.AuditExtensions.auditHeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.{AuditConnector, AuditResult}
import uk.gov.hmrc.play.audit.model.DataEvent

import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ApplicationConfig

@Singleton
class AuditService @Inject() (auditConnector: AuditConnector, appConfig: ApplicationConfig)(implicit val ec: ExecutionContext) {

  def audit(action: AuditAction, data: Map[String, String] = Map.empty)(implicit hc: HeaderCarrier): Future[AuditResult] =
    auditConnector.sendEvent(DataEvent(
      auditSource = "third-party-developer-frontend",
      auditType = action.auditType,
      tags = hc.toAuditTags(action.name, "-") ++ userContext(hc) ++ action.tags.toSeq ++ data,
      detail = hc.toAuditDetails(action.details.toSeq: _*)
    ))

  def userContext(hc: HeaderCarrier): Seq[(String, String)] = {
    def mapHeader(oldKey: String, newKey: String): Option[(String, String)] =
      hc.extraHeaders.toMap
        .get(oldKey)
        .map(value => newKey -> URLDecoder.decode(value, StandardCharsets.UTF_8.toString))

    val devEmail          = mapHeader("X-email-address", "devEmail")
    val developerFullName = mapHeader("X-name", "developerFullName")

    Seq(devEmail, developerFullName).flatten
  }
}

sealed trait AuditAction {
  val auditType: String
  val name: String
  val tags: Map[String, String]    = Map.empty
  val details: Map[String, String] = Map.empty
}

object AuditAction {

  case class ApplicationUpliftRequestDeniedDueToInvalidCredentials(applicationId: String) extends AuditAction {
    val name      = "Application uplift to production request has been denied, due to invalid credentials"
    val auditType = "ApplicationUpliftRequestDeniedDueToInvalidCredentials"

    override val details = Map(
      "applicationId" -> applicationId
    )
  }

  case class PasswordChangeFailedDueToInvalidCredentials(email: LaxEmailAddress) extends AuditAction {
    val name      = "Password change request has been denied, due to invalid credentials"
    val auditType = "PasswordChangeFailedDueToInvalidCredentials"

    override val tags = Map(
      "devEmail" -> email.text
    )
  }

  case object LoginSucceeded extends AuditAction {
    override val name: String      = "Login successful"
    override val auditType: String = "LoginSucceeded"
  }

  case object LoginFailedDueToInvalidEmail extends AuditAction {
    val name      = "Login failed due to invalid email address"
    val auditType = "LoginFailedDueToInvalidEmail"
  }

  case object LoginFailedDueToInvalidPassword extends AuditAction {
    val name      = "Login failed due to invalid password"
    val auditType = "LoginFailedDueToInvalidPassword"
  }

  case object LoginFailedDueToInvalidAccessCode extends AuditAction {
    val name      = "Login failed due to invalid access code"
    val auditType = "LoginFailedDueToInvalidAccessCode"
  }

  case object LoginFailedDueToLockedAccount extends AuditAction {
    val name      = "Login failed due to locked account"
    val auditType = "LoginFailedDueToLockedAccount"
  }

  case object AccountDeletionRequested extends AuditAction {
    val name      = "Developer has requested an account deletion"
    val auditType = "AccountDeletionRequested"
  }

  case object Remove2SVRequested extends AuditAction {
    val name      = "Developer has requested 2SV removal"
    val auditType = "Remove2SVRequested"
  }

  case object ApplicationDeletionRequested extends AuditAction {
    override val name: String      = "Developer has requested application deletion"
    override val auditType: String = "ApplicationDeletionRequest"
  }

  case object UserLogoutSurveyCompleted extends AuditAction {
    val name      = "Developer has submitted log out survey"
    val auditType = "LogOutSurveySubmission"
  }
}
