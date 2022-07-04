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

package uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications

import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.{DeveloperSession, UserId}
import play.api.libs.json.Json
import uk.gov.hmrc.play.json.Union

import java.time.LocalDateTime

trait ApplicationUpdate {
  def instigator: UserId
  def timestamp: LocalDateTime
}

case class ChangeProductionApplicationPrivacyPolicyLocation(instigator: UserId, timestamp: LocalDateTime, oldLocation: PrivacyPolicyLocation, newLocation: PrivacyPolicyLocation) extends ApplicationUpdate

trait ApplicationUpdateFormatters {
  implicit val changePrivacyPolicyLocationFormatter = Json.format[ChangeProductionApplicationPrivacyPolicyLocation]

  implicit val applicationUpdateRequestFormatter = Union.from[ApplicationUpdate]("updateType")
    .and[ChangeProductionApplicationPrivacyPolicyLocation]("changeProductionApplicationPrivacyPolicyLocation")
    .format
}
