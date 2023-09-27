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

package uk.gov.hmrc.apiplatform.modules.submissions.domain.models

import java.time.LocalDateTime

import play.api.libs.json.Format

import uk.gov.hmrc.apiplatform.modules.common.domain.models._
import uk.gov.hmrc.apiplatform.modules.submissions.domain.models.ResponsibleIndividualVerificationState.ResponsibleIndividualVerificationState
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.ResponsibleIndividual
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.services.LocalDateTimeFormatters

case class ResponsibleIndividualVerificationId(value: String) extends AnyVal

object ResponsibleIndividualVerificationId {
  import play.api.libs.json.Json

  implicit val JsonFormat: Format[ResponsibleIndividualVerificationId] = Json.valueFormat[ResponsibleIndividualVerificationId]
}

sealed trait ResponsibleIndividualVerification {
  def id: ResponsibleIndividualVerificationId
  def applicationId: ApplicationId
  def submissionId: Submission.Id
  def submissionInstance: Int
  def applicationName: String
  def createdOn: LocalDateTime
  def state: ResponsibleIndividualVerificationState
}

case class ResponsibleIndividualToUVerification(
    id: ResponsibleIndividualVerificationId,
    applicationId: ApplicationId,
    submissionId: Submission.Id,
    submissionInstance: Int,
    applicationName: String,
    createdOn: LocalDateTime,
    state: ResponsibleIndividualVerificationState
  ) extends ResponsibleIndividualVerification

case class ResponsibleIndividualTouUpliftVerification(
    id: ResponsibleIndividualVerificationId,
    applicationId: ApplicationId,
    submissionId: Submission.Id,
    submissionInstance: Int,
    applicationName: String,
    createdOn: LocalDateTime,
    requestingAdminName: String,
    requestingAdminEmail: LaxEmailAddress,
    state: ResponsibleIndividualVerificationState
  ) extends ResponsibleIndividualVerification

case class ResponsibleIndividualUpdateVerification(
    id: ResponsibleIndividualVerificationId,
    applicationId: ApplicationId,
    submissionId: Submission.Id,
    submissionInstance: Int,
    applicationName: String,
    createdOn: LocalDateTime,
    responsibleIndividual: ResponsibleIndividual,
    requestingAdminName: String,
    requestingAdminEmail: LaxEmailAddress,
    state: ResponsibleIndividualVerificationState
  ) extends ResponsibleIndividualVerification

object ResponsibleIndividualVerification extends LocalDateTimeFormatters {
  import play.api.libs.json.Json
  import uk.gov.hmrc.play.json.Union
  implicit val utcReads = DefaultLocalDateTimeReads

  implicit val responsibleIndividualVerificationFormat          = Json.format[ResponsibleIndividualToUVerification]
  implicit val responsibleIndividualTouUpliftVerificationFormat = Json.format[ResponsibleIndividualTouUpliftVerification]
  implicit val responsibleIndividualUpdateVerificationFormat    = Json.format[ResponsibleIndividualUpdateVerification]

  implicit val jsonFormatResponsibleIndividualVerification = Union.from[ResponsibleIndividualVerification]("verificationType")
    .and[ResponsibleIndividualToUVerification]("termsOfUse")
    .and[ResponsibleIndividualTouUpliftVerification]("termsOfUseUplift")
    .and[ResponsibleIndividualUpdateVerification]("adminUpdate")
    .format

  def getVerificationType(riVerification: ResponsibleIndividualVerification): String = {
    riVerification match {
      case ritouv: ResponsibleIndividualToUVerification        => "termsOfUse"
      case ritouuv: ResponsibleIndividualTouUpliftVerification => "termsOfUseUplift"
      case riuv: ResponsibleIndividualUpdateVerification       => "adminUpdate"
    }
  }
}
