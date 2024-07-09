/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models

import java.{util => ju}
import scala.util.control.Exception._

import uk.gov.hmrc.apiplatform.modules.tpd.core.domain.models.SessionId

case class SupportSessionId(value: ju.UUID) extends SessionId {
  override def toString(): String = value.toString
}

object SupportSessionId {
  import play.api.libs.json.{Format, Json}

  implicit val format: Format[SupportSessionId] = Json.valueFormat[SupportSessionId]

  def apply(raw: String): Option[SupportSessionId] = allCatch.opt(SupportSessionId(ju.UUID.fromString(raw)))

  def unsafeApply(raw: String): SupportSessionId = apply(raw).getOrElse(throw new RuntimeException(s"$raw is not a valid SupportSessionId"))

// $COVERAGE-OFF$
  def random: SupportSessionId = SupportSessionId(ju.UUID.randomUUID())
// $COVERAGE-ON$
}
