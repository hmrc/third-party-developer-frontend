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

import org.joda.time.DateTime
import uk.gov.hmrc.time.DateTimeUtils

case class ApplicationState(
                             name: State,
                             requestedByEmailAddress: Option[String],
                             verificationCode: Option[String] = None,
                             updatedOn: DateTime = DateTimeUtils.now
                           )

object ApplicationState {
  import play.api.libs.json.Json
  import play.api.libs.json.JodaReads._
  import play.api.libs.json.JodaWrites._

  implicit val format = Json.format[ApplicationState]

  val testing = ApplicationState(State.TESTING, None)

  def pendingGatekeeperApproval(requestedBy: String) =
    ApplicationState(State.PENDING_GATEKEEPER_APPROVAL, Some(requestedBy))

  def pendingRequesterVerification(requestedBy: String, verificationCode: String) =
    ApplicationState(State.PENDING_REQUESTER_VERIFICATION, Some(requestedBy), Some(verificationCode))

  def production(requestedBy: String, verificationCode: String) =
    ApplicationState(State.PRODUCTION, Some(requestedBy), Some(verificationCode))
}
