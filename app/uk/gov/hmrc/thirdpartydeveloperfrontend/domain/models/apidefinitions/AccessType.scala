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

package uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.apidefinitions

import enumeratum.{EnumEntry, PlayEnum}

sealed trait AccessType extends EnumEntry {
  def isStandard    = this == AccessType.STANDARD
  def isNotStandard = !isStandard
  def isPriviledged = this == AccessType.PRIVILEGED
  def isROPC        = this == AccessType.ROPC
}

object AccessType extends PlayEnum[AccessType] {
  val values = findValues

  final case object STANDARD   extends AccessType
  final case object PRIVILEGED extends AccessType
  final case object ROPC       extends AccessType
}
