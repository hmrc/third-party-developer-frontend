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

sealed trait TermsAndConditionsLocation

object TermsAndConditionsLocation {
  case object NoneProvided extends TermsAndConditionsLocation
  case object InDesktopSoftware extends TermsAndConditionsLocation
  case class Url(value: String) extends TermsAndConditionsLocation

  def asText(location: TermsAndConditionsLocation) =
    location match {
      case TermsAndConditionsLocation.Url(url) => url
      case TermsAndConditionsLocation.InDesktopSoftware => "In desktop software"
      case TermsAndConditionsLocation.NoneProvided => "None"
    }

  implicit val noneProvidedFormat = Json.format[NoneProvided.type]
  implicit val inDesktopSoftwareFormat = Json.format[InDesktopSoftware.type]
  implicit val urlFormat = Json.format[Url]

  implicit val format = Union.from[TermsAndConditionsLocation]("termsAndConditionsType")
    .and[NoneProvided.type]("noneProvided")
    .and[InDesktopSoftware.type]("inDesktop")
    .and[Url]("url")
    .format
}
