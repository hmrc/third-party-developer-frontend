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

import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.{ApplicationName, Collaborator}
import uk.gov.hmrc.apiplatform.modules.common.domain.models.{ApplicationId, Environment, LaxEmailAddress}

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

  def createForRequestApplicationDeletion(
      name: String,
      requestedByEmail: LaxEmailAddress,
      role: Collaborator.Role,
      environment: Environment,
      applicationName: ApplicationName,
      applicationId: ApplicationId,
      deleteRestricted: Boolean
    ): CreateTicketRequest = {

    val actor = role match {
      case Collaborator.Roles.ADMINISTRATOR => "an administrator"
      case _                                => "a developer"
    }

    def message() =
      if (deleteRestricted) {
        s"""I am $actor on the following ${environment.toString.toLowerCase} application '$applicationName'
           |and the application id is '${applicationId}'. I want it to be deleted from the Developer Hub. 
           |Please note that this application is marked as restricted for delete.""".stripMargin
      } else {
        s"""I am $actor on the following ${environment.toString.toLowerCase} application '$applicationName'
           |and the application id is '${applicationId}'. I want it to be deleted from the Developer Hub.""".stripMargin
      }
    CreateTicketRequest(
      fullName = name,
      email = requestedByEmail.text,
      subject = "Production Application Delete Request",
      message = message(),
      applicationId = Some(applicationId.toString()),
      supportReason = Some("Production Application Delete Request")
    )
  }

  def deleteDeveloperAccount(developerName: String, developerEmail: LaxEmailAddress): CreateTicketRequest = {
    val ticketMessage =
      s"""I '${developerEmail.text}' want my Developer Hub account to be deleted"""

    CreateTicketRequest(
      fullName = developerName,
      email = developerEmail.text,
      subject = "Delete Developer Account Request",
      message = ticketMessage,
      supportReason = Some("Delete Developer Account Request")
    )
  }

  def removeDeveloper2SV(developerName: String, developerEmail: LaxEmailAddress): CreateTicketRequest = {
    val ticketMessage =
      s"""I '${developerEmail.text}' want my 2SV to be removed"""

    CreateTicketRequest(
      fullName = developerName,
      email = developerEmail.text,
      subject = "2SV Removal Request",
      message = ticketMessage,
      supportReason = Some("2SV Removal Request")
    )
  }
}
