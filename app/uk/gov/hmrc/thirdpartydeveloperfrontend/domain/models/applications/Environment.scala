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

package uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications

import enumeratum.{EnumEntry, PlayEnum}

sealed trait Environment extends EnumEntry {
  def isSandbox(): Boolean = this == Environment.SANDBOX

  def isProduction(): Boolean = this == Environment.PRODUCTION
}

object Environment extends PlayEnum[Environment] {
  val values = findValues

  final case object PRODUCTION extends Environment
  final case object SANDBOX    extends Environment

  def from(env: String) = values.find(e => e.toString == env.toUpperCase)

  def prettyPrint() = this.toString().toLowerCase().capitalize
}
