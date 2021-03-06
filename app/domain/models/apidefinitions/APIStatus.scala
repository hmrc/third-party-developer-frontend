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

package domain.models.apidefinitions

import enumeratum.{EnumEntry, PlayEnum}

sealed trait APIStatus extends EnumEntry {
  def displayedStatus: String
}

object APIStatus extends PlayEnum[APIStatus] {
  val values = findValues

  final case object ALPHA       extends APIStatus { val displayedStatus = "Alpha"}
  final case object BETA        extends APIStatus { val displayedStatus = "Beta"}
  final case object STABLE      extends APIStatus { val displayedStatus = "Stable"}
  final case object DEPRECATED  extends APIStatus { val displayedStatus = "Deprecated"}
  final case object RETIRED     extends APIStatus { val displayedStatus = "Retired"}
}
