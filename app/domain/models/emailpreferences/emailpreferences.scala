/*
 * Copyright 2020 HM Revenue & Customs
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

package domain.models.emailpreferences

import enumeratum.values.{StringEnum, StringEnumEntry, StringPlayJsonValueEnum}
import play.api.libs.json.Json

case class TaxRegimeInterests(regime: String, services: Set[String])

object TaxRegimeInterests {
  implicit val format = Json.format[TaxRegimeInterests]
}

case class EmailPreferences(interests: List[TaxRegimeInterests], topics: Set[EmailTopic])

object EmailPreferences {
  implicit val format = Json.format[EmailPreferences]

  def noPreferences: EmailPreferences = EmailPreferences(List.empty, Set.empty)
}

sealed abstract class EmailTopic(val value: String, val displayName: String, val description: String, val displayOrder: Byte) extends StringEnumEntry

object EmailTopic extends StringEnum[EmailTopic] with StringPlayJsonValueEnum[EmailTopic] {

  val values = findValues

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
      Byte.MaxValue) // Event Invites is displayed separately, after the other topics

}

case class APICategory(value: String) extends AnyVal

object APICategory {
  implicit val formatApiCategory = Json.valueFormat[APICategory]
}

case class APICategoryDetails(category: String, name: String) {
  def toAPICategory(): APICategory = {
    APICategory(category)
  }
}

object APICategoryDetails {
  implicit val formatApiCategory = Json.format[APICategoryDetails]
}
