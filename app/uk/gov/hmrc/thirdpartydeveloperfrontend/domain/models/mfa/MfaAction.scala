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

package uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.mfa

import scala.collection.immutable.ListSet

sealed trait MfaAction

object MfaAction {
  case object CREATE extends MfaAction
  case object REMOVE extends MfaAction

  val values: ListSet[MfaAction] = ListSet(CREATE, REMOVE)

  def apply(text: String): Option[MfaAction] = MfaAction.values.find(_.toString() == text.toUpperCase)
  def unsafeApply(text: String): MfaAction   = apply(text).getOrElse(throw new RuntimeException(s"$text is not a valid MfaAction"))
}
