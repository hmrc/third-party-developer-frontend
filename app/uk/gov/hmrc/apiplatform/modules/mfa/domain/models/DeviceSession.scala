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

package uk.gov.hmrc.apiplatform.modules.mfa.domain.models

import java.util.UUID

import play.api.libs.json.{Json, OFormat}

import uk.gov.hmrc.apiplatform.modules.common.domain.models.UserId

case class DeviceSession(deviceSessionId: UUID, userId: UserId)

object DeviceSession {
  implicit val forma: OFormat[DeviceSession] = Json.format[DeviceSession]

}

class DeviceSessionInvalid(message: Option[String] = None) extends RuntimeException(message.orNull)
