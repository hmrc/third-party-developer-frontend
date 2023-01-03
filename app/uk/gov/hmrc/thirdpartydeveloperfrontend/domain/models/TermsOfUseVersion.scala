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

package uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models

import _root_.views.html.partials
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ApplicationConfig

object TermsOfUseVersion {
  def fromVersionString(version: String): Option[TermsOfUseVersion] = version match {
    case "1.0" => Some(OLD_JOURNEY) // some older apps in QA agreed to this version of the ToU
    case "1.1" => Some(OLD_JOURNEY) // some older apps in QA agreed to this version of the ToU
    case "1.2" => Some(OLD_JOURNEY)
    case "2.0" => Some(NEW_JOURNEY)
    case _ => None
  }

  case object OLD_JOURNEY extends TermsOfUseVersion
  case object NEW_JOURNEY extends TermsOfUseVersion

  val latest = NEW_JOURNEY
}

sealed abstract class TermsOfUseVersion  {
  import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.TermsOfUseVersion._

  def getTermsOfUseAsHtml()(implicit applicationConfig: ApplicationConfig) = this match {
    case OLD_JOURNEY => partials.termsOfUse_v12()
    case NEW_JOURNEY => partials.termsOfUse_v20()
  }

  override def toString: String = this match {
    case OLD_JOURNEY => "1.2"
    case NEW_JOURNEY => "2.0"
  }
}



