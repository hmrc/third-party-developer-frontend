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

package uk.gov.hmrc.thirdpartydeveloperfrontend.domain.services

import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.subscriptions.{FieldName, FieldValue}

trait ApplicationsJsonFormatters extends LocalDateTimeFormatters {
  import play.api.libs.json._

  import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._

  implicit val formatFieldValue: Format[FieldValue] = Json.valueFormat[FieldValue]
  implicit val formatFieldName: Format[FieldName]   = Json.valueFormat[FieldName]

  implicit val keyReadsFieldName: KeyReads[FieldName]   = key => JsSuccess(FieldName(key))
  implicit val keyWritesFieldName: KeyWrites[FieldName] = _.value

  object TOUAHelper extends LocalDateTimeFormatters {

    val formatTOUA: OFormat[TermsOfUseAgreement] = Json.format[TermsOfUseAgreement]
  }

  implicit val formatTermsOfUseAgreement: OFormat[TermsOfUseAgreement] = TOUAHelper.formatTOUA

  implicit val formatApplication: OFormat[Application] = Json.format[Application]

  implicit val format: OFormat[ApplicationWithSubscriptionData] = Json.format[ApplicationWithSubscriptionData]
}

object ApplicationsJsonFormatters extends ApplicationsJsonFormatters
