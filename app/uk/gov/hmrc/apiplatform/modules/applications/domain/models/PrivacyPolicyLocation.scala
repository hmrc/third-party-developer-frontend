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

package uk.gov.hmrc.apiplatform.modules.applications.domain.models

sealed trait PrivacyPolicyLocation {
  def describe(): String = PrivacyPolicyLocations.describe(this)
}

object PrivacyPolicyLocations {
  case object NoneProvided      extends PrivacyPolicyLocation
  case object InDesktopSoftware extends PrivacyPolicyLocation
  case class Url(value: String) extends PrivacyPolicyLocation

  def describe(privacyPolicyLocation: PrivacyPolicyLocation): String = {
    privacyPolicyLocation match {
      case InDesktopSoftware => "In desktop software"
      case Url(value)        => value
      case _                 => "None provided"
    }
  }
}

object PrivacyPolicyLocation {
  import play.api.libs.json.Json
  import uk.gov.hmrc.play.json.Union

  private implicit val noneProvidedFormat      = Json.format[PrivacyPolicyLocations.NoneProvided.type]
  private implicit val inDesktopSoftwareFormat = Json.format[PrivacyPolicyLocations.InDesktopSoftware.type]
  private implicit val urlFormat               = Json.format[PrivacyPolicyLocations.Url]

  implicit val privacyPolicyLocationFormat = Union.from[PrivacyPolicyLocation]("privacyPolicyType")
    .and[PrivacyPolicyLocations.NoneProvided.type]("noneProvided")
    .and[PrivacyPolicyLocations.InDesktopSoftware.type]("inDesktop")
    .and[PrivacyPolicyLocations.Url]("url")
    .format
}
