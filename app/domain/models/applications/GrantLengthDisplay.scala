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

package domain.models.applications

import enumeratum.values._

sealed abstract class GrantLengthDisplay(val value: Int, val name: String) extends IntEnumEntry

case object GrantLengthDisplay extends IntPlayEnum[GrantLengthDisplay] {
  case object OneMonth extends GrantLengthDisplay(value = 30, name = "1 month")
  case object ThreeMonths extends GrantLengthDisplay(value = 90, name = "3 months")
  case object SixMonths extends GrantLengthDisplay(value = 180, name = "6 months")
  case object OneYear extends GrantLengthDisplay(value = 365, name = "1 year")
  case object EighteenMonths extends GrantLengthDisplay(value = 547, name = "18 months")
  case object ThreeYears extends GrantLengthDisplay(value = 1095, name = "3 years")
  case object FiveYears extends GrantLengthDisplay(value = 1825, name = "5 years")
  case object TenYears extends GrantLengthDisplay(value = 3650, name = "10 years")
  case object HundredYears extends GrantLengthDisplay(value = 36500, name = "100 years")

  val values = findValues
}
