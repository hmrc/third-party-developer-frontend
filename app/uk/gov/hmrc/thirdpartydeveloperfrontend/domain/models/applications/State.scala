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

sealed trait State extends EnumEntry {
  def isPreProduction: Boolean = this == State.PRE_PRODUCTION
  def isProduction: Boolean    = this == State.PRODUCTION

  def isApproved: Boolean = isPreProduction || isProduction

  def isPendingApproval: Boolean = (this == State.PENDING_REQUESTER_VERIFICATION
    || this == State.PENDING_GATEKEEPER_APPROVAL
    || this == State.PENDING_RESPONSIBLE_INDIVIDUAL_VERIFICATION)

  def isInTesting: Boolean = this == State.TESTING
}

object State extends PlayEnum[State] {
  val values = findValues

  final case object TESTING                                     extends State
  final case object PENDING_RESPONSIBLE_INDIVIDUAL_VERIFICATION extends State
  final case object PENDING_GATEKEEPER_APPROVAL                 extends State
  final case object PENDING_REQUESTER_VERIFICATION              extends State
  final case object PRE_PRODUCTION                              extends State
  final case object PRODUCTION                                  extends State
  final case object DELETED                                     extends State
}
