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

sealed trait EmailTopic {
  lazy val displayOrder = EmailTopic.displayOrder(this)
  lazy val displayName  = EmailTopic.displayName(this)
  lazy val description  = EmailTopic.description(this)
}

object EmailTopic {

  case object BUSINESS_AND_POLICY extends EmailTopic
  case object TECHNICAL           extends EmailTopic
  case object RELEASE_SCHEDULES   extends EmailTopic
  case object EVENT_INVITES       extends EmailTopic

  val values: List[EmailTopic] = List(BUSINESS_AND_POLICY, TECHNICAL, RELEASE_SCHEDULES, EVENT_INVITES)

  def displayOrder(et: EmailTopic): Byte = et match {
    case BUSINESS_AND_POLICY => 1
    case TECHNICAL           => 2
    case RELEASE_SCHEDULES   => 3
    case EVENT_INVITES       => Byte.MaxValue
  }

  def displayName(et: EmailTopic): String = et match {
    case BUSINESS_AND_POLICY => "Business and policy"
    case TECHNICAL           => "Technical"
    case RELEASE_SCHEDULES   => "Release schedules"
    case EVENT_INVITES       => "Email invites"
  }

  def description(et: EmailTopic): String = et match {
    case BUSINESS_AND_POLICY => "Policy compliance, legislative changes and business guidance support"
    case TECHNICAL           => "Specifications, service guides, bug fixes and known errors"
    case RELEASE_SCHEDULES   => "Notifications about planned releases and outages"
    case EVENT_INVITES       => "Get invites to knowledge share events and user research opportunities"
  }

  def apply(text: String): Option[EmailTopic] = EmailTopic.values.find(_.toString() == text.toUpperCase)
  def unsafeApply(text: String): EmailTopic   = apply(text).getOrElse(throw new RuntimeException(s"$text is not a valid Email Topic"))

  import play.api.libs.json.Format
  import uk.gov.hmrc.apiplatform.modules.common.domain.services.SealedTraitJsonFormatting
  implicit val format: Format[EmailTopic] = SealedTraitJsonFormatting.createFormatFor[EmailTopic]("Email Topic", EmailTopic.apply)
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
