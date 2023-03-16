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

sealed trait TermsAndConditionsLocation {
  def describe(): String = TermsAndConditionsLocations.describe(this)
}

object TermsAndConditionsLocations {
  case object NoneProvided      extends TermsAndConditionsLocation
  case object InDesktopSoftware extends TermsAndConditionsLocation
  case class Url(value: String) extends TermsAndConditionsLocation

  def describe(termsAndConditionsLocation: TermsAndConditionsLocation): String = {
    termsAndConditionsLocation match {
      case InDesktopSoftware => "In desktop software"
      case Url(value)        => value
      case _                 => "None provided"
    }
  }
}

object TermsAndConditionsLocation {
  import play.api.libs.json.Json
  import uk.gov.hmrc.play.json.Union

  private implicit val noneProvidedFormat      = Json.format[TermsAndConditionsLocations.NoneProvided.type]
  private implicit val inDesktopSoftwareFormat = Json.format[TermsAndConditionsLocations.InDesktopSoftware.type]
  private implicit val urlFormat               = Json.format[TermsAndConditionsLocations.Url]

  implicit val termsAndConditionsLocationFormat = Union.from[TermsAndConditionsLocation]("termsAndConditionsType")
    .and[TermsAndConditionsLocations.NoneProvided.type]("noneProvided")
    .and[TermsAndConditionsLocations.InDesktopSoftware.type]("inDesktop")
    .and[TermsAndConditionsLocations.Url]("url")
    .format
}
