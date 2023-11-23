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

package uk.gov.hmrc.apiplatform.modules.mfa.models

import java.time.LocalDateTime
import java.util.UUID
import scala.collection.immutable

import enumeratum.{Enum, EnumEntry, PlayJsonEnum}

import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.play.json.Union
import play.api.libs.json.Format

case class MfaId(value: UUID) extends AnyVal {
  override def toString(): String = value.toString()
}

object MfaId {
  def random: MfaId = MfaId(UUID.randomUUID())

  implicit val mfaIdFormat: Format[MfaId] = Json.valueFormat[MfaId]
}

sealed trait MfaType extends EnumEntry {
  def asText: String
}

object MfaType extends Enum[MfaType] with PlayJsonEnum[MfaType] {
  val values: immutable.IndexedSeq[MfaType] = findValues

  case object AUTHENTICATOR_APP extends MfaType {
    override def asText: String = "Authenticator app"
  }

  case object SMS extends MfaType {
    override def asText: String = "Text message"
  }

}

sealed trait MfaDetail {
  val id: MfaId
  val name: String
  def mfaType: MfaType
  def createdOn: LocalDateTime
  def verified: Boolean
}

case class AuthenticatorAppMfaDetailSummary(override val id: MfaId, override val name: String, override val createdOn: LocalDateTime, verified: Boolean = false) extends MfaDetail {
  override val mfaType: MfaType = MfaType.AUTHENTICATOR_APP
}

case class SmsMfaDetailSummary(
    override val id: MfaId = MfaId.random,
    override val name: String,
    override val createdOn: LocalDateTime,
    mobileNumber: String,
    verified: Boolean = false
  ) extends MfaDetail {
  override val mfaType: MfaType = MfaType.SMS
}

object MfaDetailFormats {

  implicit val authenticatorAppMfaDetailFormat: OFormat[AuthenticatorAppMfaDetailSummary] = Json.format[AuthenticatorAppMfaDetailSummary]
  implicit val smsMfaDetailSummaryFormat: OFormat[SmsMfaDetailSummary]                    = Json.format[SmsMfaDetailSummary]

  implicit val mfaDetailFormat: OFormat[MfaDetail] = Union.from[MfaDetail]("mfaType")
    .and[AuthenticatorAppMfaDetailSummary](MfaType.AUTHENTICATOR_APP.toString)
    .and[SmsMfaDetailSummary](MfaType.SMS.toString)
    .format

}

sealed trait MfaAction extends EnumEntry

object MfaAction extends Enum[MfaAction] with PlayJsonEnum[MfaAction] {
  val values: immutable.IndexedSeq[MfaAction] = findValues

  case object CREATE extends MfaAction

  case object REMOVE extends MfaAction
}
