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

package domain.models.developers

import enumeratum.{EnumEntry, PlayEnum}

sealed trait LoggedInState extends EnumEntry {
  def isLoggedIn: Boolean = this == LoggedInState.LOGGED_IN
  def isPartLoggedInEnablingMFA: Boolean = this == LoggedInState.PART_LOGGED_IN_ENABLING_MFA
}

object LoggedInState extends PlayEnum[LoggedInState] {

  val values = findValues

  final case object LOGGED_IN extends LoggedInState

  final case object PART_LOGGED_IN_ENABLING_MFA extends LoggedInState

}
