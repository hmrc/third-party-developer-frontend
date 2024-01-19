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

package uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers

sealed trait LoggedInState {
  def isLoggedIn: Boolean                = this == LoggedInState.LOGGED_IN
  def isPartLoggedInEnablingMFA: Boolean = this == LoggedInState.PART_LOGGED_IN_ENABLING_MFA
}

object LoggedInState {
  final case object LOGGED_IN extends LoggedInState

  final case object PART_LOGGED_IN_ENABLING_MFA extends LoggedInState

  val values = List(LOGGED_IN, PART_LOGGED_IN_ENABLING_MFA)

  def apply(text: String): Option[LoggedInState] = LoggedInState.values.find(_.toString() == text.toUpperCase)

  import play.api.libs.json.Format
  import uk.gov.hmrc.apiplatform.modules.common.domain.services.SealedTraitJsonFormatting
  implicit val format: Format[LoggedInState] = SealedTraitJsonFormatting.createFormatFor[LoggedInState]("Logged In State", LoggedInState.apply)
}
