/*
 * Copyright 2021 HM Revenue & Customs
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

import domain.models.flows.{Flow, IpAllowlistFlow}
import org.joda.time.DateTime
import play.api.libs.json.{Format, Json, OFormat}
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import uk.gov.hmrc.play.json.Union
import domain.models.flows.{EmailPreferencesFlow, NewApplicationEmailPreferencesFlow}
import domain.models.flows.FlowType

object ReactiveMongoFormatters {
  implicit val dateFormat: Format[DateTime] = ReactiveMongoFormats.dateTimeFormats
  implicit val formatIpAllowlistFlow: OFormat[IpAllowlistFlow] = Json.format[IpAllowlistFlow]
  implicit val formatEmailPreferencesFlow: OFormat[EmailPreferencesFlow] = Json.format[EmailPreferencesFlow]
  implicit val formatNewApplicationEmailPreferencesFlow: OFormat[NewApplicationEmailPreferencesFlow] = Json.format[NewApplicationEmailPreferencesFlow]
  implicit val formatFlow: Format[Flow] = Union.from[Flow]("flowType")
    .and[IpAllowlistFlow](FlowType.IP_ALLOW_LIST.toString())
    .and[EmailPreferencesFlow](FlowType.EMAIL_PREFERENCES.toString())
    .and[NewApplicationEmailPreferencesFlow](FlowType.NEW_APPLICATION_EMAIL_PREFERENCES.toString())
    .format
}
