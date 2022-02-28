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

package uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models

import _root_.views.html.partials
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ApplicationConfig

object TermsOfUseVersion {
  def fromVersionString(version: String): Option[TermsOfUseVersion] = version match {
    case "1.0" => Some(V1_2) // some older apps in QA agreed to this version of the ToU
    case "1.1" => Some(V1_2) // some older apps in QA agreed to this version of the ToU
    case "1.2" => Some(V1_2)
    case "2.0" => Some(V2_0)
    case _ => None
  }

  case object V1_2 extends TermsOfUseVersion
  case object V2_0 extends TermsOfUseVersion

  val latest = V2_0
}

sealed abstract class TermsOfUseVersion  {
  import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.TermsOfUseVersion._

  def getTermsOfUseAsHtml()(implicit applicationConfig: ApplicationConfig) = this match {
    case V1_2 => partials.termsOfUse_v12()
    case V2_0 => partials.termsOfUse_v20()
  }

  override def toString: String = this match {
    case V1_2 => "1.2"
    case V2_0 => "2.0"
  }
}



