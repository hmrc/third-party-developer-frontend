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

package uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications

import play.api.libs.json._
import uk.gov.hmrc.play.json.Union

sealed trait PrivacyPolicyLocation

object PrivacyPolicyLocation {
  case object NoneProvided      extends PrivacyPolicyLocation
  case object InDesktopSoftware extends PrivacyPolicyLocation
  case class Url(value: String) extends PrivacyPolicyLocation

  def asText(location: PrivacyPolicyLocation) =
    location match {
      case PrivacyPolicyLocation.Url(url)          => url
      case PrivacyPolicyLocation.InDesktopSoftware => "In desktop software"
      case PrivacyPolicyLocation.NoneProvided      => "None"
    }

  implicit val noneProvidedFormat      = Json.format[NoneProvided.type]
  implicit val inDesktopSoftwareFormat = Json.format[InDesktopSoftware.type]
  implicit val urlFormat               = Json.format[Url]

  implicit val format = Union.from[PrivacyPolicyLocation]("privacyPolicyType")
    .and[NoneProvided.type]("noneProvided")
    .and[InDesktopSoftware.type]("inDesktop")
    .and[Url]("url")
    .format
}
