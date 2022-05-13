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

package uk.gov.hmrc.apiplatform.modules.mfa.models

import play.api.libs.json.Json
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.UserId

import java.util.UUID

case class DeviceSession(deviceSessionId: UUID, userId: UserId)

object DeviceSession {
  implicit val format = Json.format[DeviceSession]

}

class DeviceSessionInvalid(message: Option[String] = None) extends RuntimeException(message.orNull)