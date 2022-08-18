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

package uk.gov.hmrc.apiplatform.modules.submissions.domain.services

import uk.gov.hmrc.apiplatform.modules.submissions.domain.models.{ResponsibleIndividualVerification, ResponsibleIndividualToUVerification, ResponsibleIndividualVerificationWithDetails, ResponsibleIndividualUpdateVerification}
import play.api.libs.json._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.services.LocalDateTimeFormatters

trait ResponsibleIndividualVerificationFrontendJsonFormatters extends LocalDateTimeFormatters {

  import uk.gov.hmrc.play.json.Union
  implicit val utcReads = DefaultLocalDateTimeReads

  implicit val responsibleIndividualVerificationFormat            = Json.format[ResponsibleIndividualToUVerification]
  implicit val responsibleIndividualUpdateVerificationFormat      = Json.format[ResponsibleIndividualUpdateVerification]
  implicit val responsibleIndividualVerificationWithDetailsFormat = Json.format[ResponsibleIndividualVerificationWithDetails]
  
  implicit val jsonFormatResponsibleIndividualVerification = Union.from[ResponsibleIndividualVerification]("verificationType")
    .and[ResponsibleIndividualToUVerification]("termsOfUse")
    .and[ResponsibleIndividualUpdateVerification]("adminUpdate")
    .format
}

object ResponsibleIndividualVerificationFrontendJsonFormatters extends ResponsibleIndividualVerificationFrontendJsonFormatters
