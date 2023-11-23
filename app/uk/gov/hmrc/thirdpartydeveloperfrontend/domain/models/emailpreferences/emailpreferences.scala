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

package uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.emailpreferences

import enumeratum.values.{StringEnum, StringEnumEntry, StringPlayJsonValueEnum}
import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.apiplatform.modules.apis.domain.models.ApiCategory

case class TaxRegimeInterests(regime: String, services: Set[String]) {
  def addService(serviceName: String): TaxRegimeInterests = copy(services = services ++ Set(serviceName))
}

object TaxRegimeInterests {
  implicit val format: OFormat[TaxRegimeInterests] = Json.format[TaxRegimeInterests]
}

case class EmailPreferences(interests: List[TaxRegimeInterests], topics: Set[EmailTopic])

object EmailPreferences {
  implicit val format: OFormat[EmailPreferences] = Json.format[EmailPreferences]

  def noPreferences: EmailPreferences = EmailPreferences(List.empty, Set.empty)
}

sealed abstract class EmailTopic(val value: String, val displayName: String, val description: String, val displayOrder: Byte) extends StringEnumEntry

object EmailTopic extends StringEnum[EmailTopic] with StringPlayJsonValueEnum[EmailTopic] {

  val values: IndexedSeq[EmailTopic] = findValues

  case object BUSINESS_AND_POLICY
      extends EmailTopic("BUSINESS_AND_POLICY", "Business and policy", "Policy compliance, legislative changes and business guidance support", 1)

  case object TECHNICAL
      extends EmailTopic("TECHNICAL", "Technical", "Specifications, service guides, bug fixes and known errors", 2)

  case object RELEASE_SCHEDULES
      extends EmailTopic("RELEASE_SCHEDULES", "Release schedules", "Notifications about planned releases and outages", 3)

  case object EVENT_INVITES
      extends EmailTopic(
        "EVENT_INVITES",
        "Event invites",
        "Get invites to knowledge share events and user research opportunities",
        Byte.MaxValue
      ) // Event Invites is displayed separately, after the other topics

}

// TODO - make category an APICategory
case class APICategoryDisplayDetails(category: String, name: String) {

  def toAPICategory(): ApiCategory = {
    ApiCategory.unsafeApply(category)
  }
}

object APICategoryDisplayDetails {
  implicit val formatApiCategory: OFormat[APICategoryDisplayDetails] = Json.format[APICategoryDisplayDetails]

  def from(category: ApiCategory): APICategoryDisplayDetails = APICategoryDisplayDetails(category.toString, category.displayText)
}
