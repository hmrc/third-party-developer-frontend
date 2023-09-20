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

import play.api.libs.json.Json
import uk.gov.hmrc.play.json.Union

import uk.gov.hmrc.apiplatform.modules.applications.domain.models.{PrivacyPolicyLocation, TermsAndConditionsLocation}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.apidefinitions.AccessType


case class ImportantSubmissionData(
    organisationUrl: Option[String] = None,
    responsibleIndividual: ResponsibleIndividual,
    serverLocations: Set[ServerLocation],
    termsAndConditionsLocation: TermsAndConditionsLocation,
    privacyPolicyLocation: PrivacyPolicyLocation,
    termsOfUseAcceptances: List[TermsOfUseAcceptance]
  )

object ImportantSubmissionData {
  implicit val format = Json.format[ImportantSubmissionData]
}

sealed trait Access {
  val accessType: AccessType

  def hasSubmissions: Boolean
}

object Access {
  import play.api.libs.json.Json

  implicit val formatStandard   = Json.format[Standard]
  implicit val formatPrivileged = Json.format[Privileged]
  implicit val formatROPC       = Json.format[ROPC]

  implicit val format = Union.from[Access]("accessType")
    .and[Standard](AccessType.STANDARD.toString)
    .and[Privileged](AccessType.PRIVILEGED.toString)
    .and[ROPC](AccessType.ROPC.toString)
    .format
}

case class Standard(
    redirectUris: List[String] = List.empty,
    termsAndConditionsUrl: Option[String] = None,
    privacyPolicyUrl: Option[String] = None,
    overrides: Set[OverrideFlag] = Set.empty,
    sellResellOrDistribute: Option[SellResellOrDistribute] = None,
    importantSubmissionData: Option[ImportantSubmissionData] = None
  ) extends Access {
  override val accessType = AccessType.STANDARD

  lazy val hasSubmissions: Boolean = importantSubmissionData.nonEmpty
}

case class Privileged(scopes: Set[String] = Set.empty) extends Access {
  override val accessType = AccessType.PRIVILEGED

  val hasSubmissions: Boolean = false
}

case class ROPC(scopes: Set[String] = Set.empty) extends Access {
  override val accessType = AccessType.ROPC

  val hasSubmissions: Boolean = false
}
