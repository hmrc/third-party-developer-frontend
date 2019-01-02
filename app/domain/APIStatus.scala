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

sealed trait APIStatus extends EnumEntry

object APIStatus extends PlayEnum[APIStatus] {
  val values = findValues

  final case object ALPHA       extends APIStatus
  final case object BETA        extends APIStatus
  final case object STABLE      extends APIStatus
  final case object DEPRECATED  extends APIStatus
  final case object RETIRED     extends APIStatus
}
