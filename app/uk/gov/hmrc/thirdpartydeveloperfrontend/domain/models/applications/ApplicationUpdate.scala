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

package uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications

import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.UserId
import play.api.libs.json.Json
import uk.gov.hmrc.play.json.Union

import java.time.LocalDateTime

trait ApplicationUpdate {
  def timestamp: LocalDateTime
}

case class ChangeProductionApplicationPrivacyPolicyLocation(instigator: UserId, timestamp: LocalDateTime, newLocation: PrivacyPolicyLocation) extends ApplicationUpdate
case class ChangeProductionApplicationTermsAndConditionsLocation(instigator: UserId, timestamp: LocalDateTime, newLocation: TermsAndConditionsLocation) extends ApplicationUpdate
case class ChangeResponsibleIndividualToSelf(instigator: UserId, timestamp: LocalDateTime, name: String, email: String) extends ApplicationUpdate
case class ChangeResponsibleIndividualToOther(code: String, timestamp: LocalDateTime) extends ApplicationUpdate
case class VerifyResponsibleIndividual(instigator: UserId, timestamp: LocalDateTime, requesterName: String, riName: String, riEmail: String) extends ApplicationUpdate
case class DeclineResponsibleIndividual(code: String, timestamp: LocalDateTime) extends ApplicationUpdate

trait ApplicationUpdateFormatters {
  implicit val changePrivacyPolicyLocationFormatter = Json.format[ChangeProductionApplicationPrivacyPolicyLocation]
  implicit val changeTermsAndConditionsLocationFormatter = Json.format[ChangeProductionApplicationTermsAndConditionsLocation]
  implicit val changeResponsibleIndividualToSelfFormatter = Json.format[ChangeResponsibleIndividualToSelf]
  implicit val changeResponsibleIndividualToOtherFormatter = Json.format[ChangeResponsibleIndividualToOther]
  implicit val verifyResponsibleIndividualFormatter = Json.format[VerifyResponsibleIndividual]
  implicit val declineResponsibleIndividualFormatter = Json.format[DeclineResponsibleIndividual]

  implicit val applicationUpdateRequestFormatter = Union.from[ApplicationUpdate]("updateType")
    .and[ChangeProductionApplicationPrivacyPolicyLocation]("changeProductionApplicationPrivacyPolicyLocation")
    .and[ChangeProductionApplicationTermsAndConditionsLocation]("changeProductionApplicationTermsAndConditionsLocation")
    .and[ChangeResponsibleIndividualToSelf]("changeResponsibleIndividualToSelf")
    .and[ChangeResponsibleIndividualToOther]("changeResponsibleIndividualToOther")
    .and[VerifyResponsibleIndividual]("verifyResponsibleIndividual")
    .and[DeclineResponsibleIndividual]("declineResponsibleIndividual")
    .format
}
