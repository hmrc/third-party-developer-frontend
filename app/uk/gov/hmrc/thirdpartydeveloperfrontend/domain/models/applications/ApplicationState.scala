/*
 * Copyright 2022 HM Revenue & Customs
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

import java.time.{LocalDateTime, ZoneOffset}

case class ApplicationState(
 name: State,
 requestedByEmailAddress: Option[String],
 requestedByName: Option[String],
 verificationCode: Option[String] = None,
 updatedOn: LocalDateTime = LocalDateTime.now(ZoneOffset.UTC)
) {
  def isInTesting = name.isInTesting
  def isPendingApproval = name.isPendingApproval
  def isApproved = name.isApproved
}

object ApplicationState {
  import play.api.libs.json.Json

  implicit val format = Json.format[ApplicationState]

  val testing = ApplicationState(State.TESTING, None, None)

  def pendingGatekeeperApproval(requestedBy: String) =
    ApplicationState(State.PENDING_GATEKEEPER_APPROVAL, Some(requestedBy), Some(requestedBy))

  def pendingResponsibleIndividualVerification(requestedByEmail: String, requestedByName: String) =
    ApplicationState(State.PENDING_RESPONSIBLE_INDIVIDUAL_VERIFICATION, Some(requestedByEmail), Some(requestedByName))

  def pendingRequesterVerification(requestedBy: String, verificationCode: String) =
    ApplicationState(State.PENDING_REQUESTER_VERIFICATION, Some(requestedBy), Some(requestedBy), Some(verificationCode))

  def preProduction(requestedBy: String) =
    ApplicationState(State.PRE_PRODUCTION, Some(requestedBy), Some(requestedBy), None)

  def production(requestedBy: String, verificationCode: String) =
    ApplicationState(State.PRODUCTION, Some(requestedBy), Some(requestedBy), Some(verificationCode))
}
