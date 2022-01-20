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

package uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers

import java.{util => ju}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.emailpreferences.EmailPreferences
import play.api.libs.json.{Format, Json}

case class UserId(value: ju.UUID) extends AnyVal {
  def asText = value.toString
}

object UserId {
  import play.api.libs.json.Json
  implicit val developerIdFormat = Json.valueFormat[UserId]

  def random: UserId = UserId(ju.UUID.randomUUID())
}

case class Developer(
  userId: UserId,
  email: String,
  firstName: String,
  lastName: String,
  organisation: Option[String] = None,
  mfaEnabled: Option[Boolean] = None,
  emailPreferences: EmailPreferences = EmailPreferences.noPreferences
)

object Developer {
  implicit val format: Format[Developer] = Json.format[Developer]
}

