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
import uk.gov.hmrc.apiplatform.modules.common.domain.models.{ApiVersionNbr, ApplicationId, Environment, LaxEmailAddress}

case class CreateTicketRequest(
    fullName: String,
    email: String,
    subject: String,
    message: String,
    apiName: Option[String] = None,
    applicationId: Option[String] = None,
    organisation: Option[String] = None,
    supportReason: Option[String] = None,
    reasonKey: Option[String] = None,
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
      supportReason = Some("Production Application Name Change"),
      reasonKey = Some("prod-app-name-change")
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

    def ticketMessage() =
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
      message = ticketMessage(),
      applicationId = Some(applicationId.toString()),
      supportReason = Some("Production Application Delete Request"),
      reasonKey = Some("prod-app-delete")
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
      supportReason = Some("Delete Developer Account Request"),
      reasonKey = Some("developer-delete")
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
      supportReason = Some("2SV Removal Request"),
      reasonKey = Some("remove-2sv")
    )
  }

  def createForApiSubscribe(
      requestorName: String,
      requestorEmail: LaxEmailAddress,
      applicationName: ApplicationName,
      applicationId: ApplicationId,
      apiName: String,
      apiVersion: ApiVersionNbr
    ): CreateTicketRequest = {
    val ticketMessage = s"""I '${requestorEmail.text}' want my application '$applicationName'
                           |identified by '${applicationId}'
                           |to be subscribed to the API '$apiName'
                           |with version '${apiVersion.value}'""".stripMargin

    CreateTicketRequest(
      fullName = requestorName,
      email = requestorEmail.text,
      subject = "Production Application Subscription Request",
      message = ticketMessage,
      applicationId = Some(applicationId.toString()),
      supportReason = Some("Production Application Subscription Request"),
      reasonKey = Some("prod-app-subscribe")
    )
  }

  def createForApiUnsubscribe(
      requestorName: String,
      requestorEmail: LaxEmailAddress,
      applicationName: ApplicationName,
      applicationId: ApplicationId,
      apiName: String,
      apiVersion: ApiVersionNbr
    ): CreateTicketRequest = {
    val ticketMessage = s"""I '${requestorEmail.text}' want my application '$applicationName'
                           |identified by '${applicationId}'
                           |to be unsubscribed from the API '$apiName'
                           |with version '${apiVersion.value}'""".stripMargin

    CreateTicketRequest(
      fullName = requestorName,
      email = requestorEmail.text,
      subject = "Production Application Unsubscribe Request",
      message = ticketMessage,
      applicationId = Some(applicationId.toString()),
      supportReason = Some("Production Application Unsubscribe Request"),
      reasonKey = Some("prod-app-unsubscribe")
    )
  }

  def createForRequestProductionCredentials(requestorName: String, requestorEmail: LaxEmailAddress, applicationName: ApplicationName, applicationId: ApplicationId)
      : CreateTicketRequest = {
    val ticketMessage =
      s"""${requestorEmail.text} submitted the following application for production use on the Developer Hub:
         |$applicationName
         |Please check it against our guidelines and send them a response within 2 working days.
         |HMRC Developer Hub
         |""".stripMargin

    CreateTicketRequest(
      fullName = requestorName,
      email = requestorEmail.text,
      subject = "Production Application Credential Request",
      message = ticketMessage,
      applicationId = Some(applicationId.toString()),
      supportReason = Some("Production Application Credential Request"),
      reasonKey = Some("prod-app-credentials")
    )
  }

  def createForTermsOfUseUplift(requestorName: String, requestorEmail: LaxEmailAddress, applicationName: ApplicationName, applicationId: ApplicationId): CreateTicketRequest = {
    val ticketMessage =
      s"""${requestorEmail.text} has submitted a Terms of Use application that has warnings or fails:
         |$applicationName
         |Please check it against our guidelines and send them a response within 2 working days.
         |HMRC Developer Hub
         |""".stripMargin

    CreateTicketRequest(
      fullName = requestorName,
      email = requestorEmail.text,
      subject = "Terms of Use - Uplift Request",
      message = ticketMessage,
      applicationId = Some(applicationId.toString()),
      supportReason = Some("Terms of Use Uplift Request"),
      reasonKey = Some("terms-of-use-uplift")
    )
  }
}
