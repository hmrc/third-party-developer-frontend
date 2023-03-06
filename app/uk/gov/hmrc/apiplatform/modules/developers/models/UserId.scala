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

package uk.gov.hmrc.apiplatform.modules.developers.domain.models

import java.{util => ju}
import scala.util.control.NonFatal

case class UserId(value: ju.UUID) extends AnyVal {
  def asText: String = value.toString
}

object UserId {
  import play.api.libs.json.Json

  implicit val userIdFormat = Json.valueFormat[UserId]

  def random: UserId = UserId(ju.UUID.randomUUID())

  def fromString(raw: String): Option[UserId] = {
    try {
      Some(UserId(ju.UUID.fromString(raw)))
    } catch {
      case NonFatal(e) => None
    }
  }

  // The unknown userId is used when the user is not logged in and is raising a support ticket
  def unknown: UserId = UserId(ju.UUID.fromString("00000000-0000-0000-0000-000000000000"))
}
