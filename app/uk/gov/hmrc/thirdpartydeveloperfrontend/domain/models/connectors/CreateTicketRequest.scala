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

package uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors

import play.api.libs.json._

import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.ApplicationName
import uk.gov.hmrc.apiplatform.modules.common.domain.models.{ApplicationId, LaxEmailAddress}

case class CreateTicketRequest(
    fullName: String,
    email: String,
    subject: String,
    message: String,
    apiName: Option[String] = None,
    applicationId: Option[String] = None,
    organisation: Option[String] = None,
    supportReason: Option[String] = None,
    teamMemberEmail: Option[String] = None
  )

object CreateTicketRequest {
  implicit val createTicketRequestFormat: Format[CreateTicketRequest] = Json.format[CreateTicketRequest]

  def createForRequestChangeOfProductionApplicationName(
      requestorName: String,
      requestorEmail: LaxEmailAddress,
      previousApplicationName: ApplicationName,
      newApplicationName: ApplicationName,
      applicationId: ApplicationId
    ): CreateTicketRequest = {
    val ticketMessage =
      s"""$requestorName wants to change the application name for $applicationId from $previousApplicationName to $newApplicationName.
         |Check if the new application name meets the naming guidelines and update Gatekeeper within 2 working days.
         |From HMRC Developer Hub
         |""".stripMargin

    CreateTicketRequest(
      fullName = requestorName,
      email = requestorEmail.text,
      subject = "Production Application Name Change",
      message = ticketMessage,
      applicationId = Some(applicationId.toString()),
      supportReason = Some("Production Application Name Change")
    )
  }
}
