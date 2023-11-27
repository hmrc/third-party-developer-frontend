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

//
//case class ApplicationState(
//    name: State,
//    requestedByEmailAddress: Option[String],
//    requestedByName: Option[String],
//    verificationCode: Option[String] = None,
//    updatedOn: LocalDateTime = LocalDateTime.now(ZoneOffset.UTC)
//  ) {
//  def isInTesting       = name.isInTesting
//  def isPendingApproval = name.isPendingApproval
//  def isApproved        = name.isApproved
//  def isProduction      = name.isProduction
//}
//
//object ApplicationState {
//  import play.api.libs.json.{Json, OFormat}
//
//  implicit val format: OFormat[ApplicationState] = Json.format[ApplicationState]
//
//  val testing = ApplicationState(State.TESTING, None, None)
//
//  def pendingGatekeeperApproval(requestedByEmail: String, requestedByName: String) =
//    ApplicationState(State.PENDING_GATEKEEPER_APPROVAL, Some(requestedByEmail), Some(requestedByName))
//
//  def pendingResponsibleIndividualVerification(requestedByEmail: String, requestedByName: String) =
//    ApplicationState(State.PENDING_RESPONSIBLE_INDIVIDUAL_VERIFICATION, Some(requestedByEmail), Some(requestedByName))
//
//  def pendingRequesterVerification(requestedByEmail: String, requestedByName: String, verificationCode: String) =
//    ApplicationState(State.PENDING_REQUESTER_VERIFICATION, Some(requestedByEmail), Some(requestedByName), Some(verificationCode))
//
//  def preProduction(requestedByEmail: String, requestedByName: String) =
//    ApplicationState(State.PRE_PRODUCTION, Some(requestedByEmail), Some(requestedByName), None)
//
//  def production(requestedByEmail: String, requestedByName: String, verificationCode: String) =
//    ApplicationState(State.PRODUCTION, Some(requestedByEmail), Some(requestedByName), Some(verificationCode))
//}
