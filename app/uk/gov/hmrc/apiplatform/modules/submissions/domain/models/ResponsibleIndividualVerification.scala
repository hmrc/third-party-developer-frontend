/*
 * Copyright 2022 HM Revenue & Customs
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

import play.api.libs.json.{Format, OFormat}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.ApplicationId
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.services.LocalDateTimeFormatters

import java.time.{LocalDateTime, ZoneOffset}

case class ResponsibleIndividualVerificationId(value: String) extends AnyVal

object ResponsibleIndividualVerificationId {
  import play.api.libs.json.Json

  implicit val JsonFormat: Format[ResponsibleIndividualVerificationId] = Json.valueFormat[ResponsibleIndividualVerificationId]
}

case class ResponsibleIndividualVerification(
    id: ResponsibleIndividualVerificationId,
    applicationId: ApplicationId,
    submissionId: Submission.Id,
    submissionInstance: Int,
    applicationName: String,
    createdOn: LocalDateTime
)

object ResponsibleIndividualVerification extends LocalDateTimeFormatters {
  def apply(id: ResponsibleIndividualVerificationId, appId: ApplicationId, appName: String, submissionId: Submission.Id, submissionInstance: Int): ResponsibleIndividualVerification =
    new ResponsibleIndividualVerification(id, appId, submissionId, submissionInstance, appName, LocalDateTime.now(ZoneOffset.UTC))

  import play.api.libs.json.Json

  implicit val formatResponsibleIndividualVerification: OFormat[ResponsibleIndividualVerification] = Json.format[ResponsibleIndividualVerification]
}
