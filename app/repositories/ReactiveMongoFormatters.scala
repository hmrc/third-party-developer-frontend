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

package repositories

import domain.models.flows._
import domain.services.CombinedApiJsonFormatters
import modules.uplift.domain.models.GetProductionCredentialsFlow
import org.joda.time.DateTime
import play.api.libs.json.{Format, Json, OFormat}
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import uk.gov.hmrc.play.json.Union

object ReactiveMongoFormatters extends CombinedApiJsonFormatters {
  implicit val dateFormat: Format[DateTime] = ReactiveMongoFormats.dateTimeFormats
  implicit val formatIpAllowlistFlow: OFormat[IpAllowlistFlow] = Json.format[IpAllowlistFlow]

  implicit val formatEmailPreferencesFlow: OFormat[EmailPreferencesFlowV2] = Json.format[EmailPreferencesFlowV2]
  implicit val formatNewApplicationEmailPreferencesFlow: OFormat[NewApplicationEmailPreferencesFlowV2] = Json.format[NewApplicationEmailPreferencesFlowV2]
  implicit val formatGetProdCredsFlow: OFormat[GetProductionCredentialsFlow] = Json.format[GetProductionCredentialsFlow]

  implicit val formatFlow: Format[Flow] = Union.from[Flow]("flowType")
    .and[IpAllowlistFlow](FlowType.IP_ALLOW_LIST.toString())
    .and[EmailPreferencesFlowV2](FlowType.EMAIL_PREFERENCES_V2.toString())
    .and[NewApplicationEmailPreferencesFlowV2](FlowType.NEW_APPLICATION_EMAIL_PREFERENCES_V2.toString())
    .and[GetProductionCredentialsFlow](FlowType.GET_PRODUCTION_CREDENTIALS.toString())
    .format
}
