/*
 * Copyright 2019 HM Revenue & Customs
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

package domain

import enumeratum.{EnumEntry, PlayEnum}

sealed trait Role extends EnumEntry

object Role extends PlayEnum[Role] {
  val values = findValues

  final case object DEVELOPER       extends Role
  final case object ADMINISTRATOR   extends Role

  def from(role: Option[String]) = role match {
    case Some(r) => values.find(e => e.toString == r.toUpperCase)
    case _ => Some(Role.DEVELOPER)}
}
