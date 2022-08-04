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

import enumeratum.{Enum, EnumEntry, PlayJsonEnum}
import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.play.json.Union

import java.time.LocalDateTime
import java.util.UUID
import scala.collection.immutable


case class MfaId(value: UUID) extends AnyVal

object MfaId {
  def random: MfaId = MfaId(UUID.randomUUID())
}

sealed trait MfaType extends EnumEntry {
  def asText: String
}

object MfaType extends Enum[MfaType] with PlayJsonEnum[MfaType] {
  val values: immutable.IndexedSeq[MfaType] = findValues

  case object AUTHENTICATOR_APP extends MfaType {
    override def asText: String = "Authenticator App"
  }
}

sealed trait MfaDetail {
  val id: MfaId
  val name: String
  def mfaType: MfaType
  def createdOn: LocalDateTime
  def verified: Boolean
}

case class AuthenticatorAppMfaDetailSummary(override val id: MfaId,
                                            override val name: String,
                                            override val createdOn: LocalDateTime,
                                            verified: Boolean = false) extends MfaDetail {
  override val mfaType: MfaType = MfaType.AUTHENTICATOR_APP
}

object MfaDetailFormats {
  implicit val mfaIdFormat= Json.valueFormat[MfaId]

  implicit val authenticatorAppMfaDetailFormat: OFormat[AuthenticatorAppMfaDetailSummary] = Json.format[AuthenticatorAppMfaDetailSummary]

  implicit val mfaDetailFormat: OFormat[MfaDetail] = Union.from[MfaDetail]("mfaType")
    .and[AuthenticatorAppMfaDetailSummary](MfaType.AUTHENTICATOR_APP.toString)
    .format

}