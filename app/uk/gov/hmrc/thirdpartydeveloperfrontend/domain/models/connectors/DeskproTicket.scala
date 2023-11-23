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
import play.api.mvc.Request

import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.Collaborator
import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress.StringSyntax
import uk.gov.hmrc.apiplatform.modules.common.domain.models.{LaxEmailAddress, _}
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers._

case class DeskproTicket(
    name: String,
    email: LaxEmailAddress,
    subject: String,
    message: String,
    referrer: String,
    userAgent: String = "",
    authId: String = "",
    areaOfTax: String = "",
    sessionId: String = "",
    javascriptEnabled: String = ""
  )

object DeskproTicket extends FieldTransformer {
  implicit val format: OFormat[DeskproTicket] = Json.format[DeskproTicket]

  def createForRequestProductionCredentials(requestorName: String, requestorEmail: LaxEmailAddress, applicationName: String, applicationId: ApplicationId): DeskproTicket = {
    val message =
      s"""${requestorEmail.text} submitted the following application for production use on the Developer Hub:
         |$applicationName
         |Please check it against our guidelines and send them a response within 2 working days.
         |HMRC Developer Hub
         |""".stripMargin

    DeskproTicket(
      requestorName,
      requestorEmail,
      "New application submitted for checking",
      message,
      uk.gov.hmrc.apiplatform.modules.submissions.controllers.routes.CheckAnswersController.checkAnswersPage(applicationId).url
    )
  }

  def createForTermsOfUseUplift(requestorName: String, requestorEmail: LaxEmailAddress, applicationName: String, applicationId: ApplicationId): DeskproTicket = {
    val message =
      s"""${requestorEmail.text} has submitted a Terms of Use application that has warnings or fails:
         |$applicationName
         |Please check it against our guidelines and send them a response within 2 working days.
         |HMRC Developer Hub
         |""".stripMargin

    DeskproTicket(
      requestorName,
      requestorEmail,
      "Terms of use uplift application submitted for checking",
      message,
      "https://admin.tax.service.gov.uk/api-gatekeeper/terms-of-use"
    )
  }

  def createForUplift(requestorName: String, requestorEmail: LaxEmailAddress, applicationName: String, applicationId: ApplicationId): DeskproTicket = {
    val message =
      s"""${requestorEmail.text} submitted the following application for production use on the Developer Hub:
         |$applicationName
         |Please check it against our guidelines and send them a response within 2 working days.
         |HMRC Developer Hub
         |""".stripMargin

    DeskproTicket(
      requestorName,
      requestorEmail,
      "New application submitted for checking",
      message,
      uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.checkpages.routes.ApplicationCheck.requestCheckPage(applicationId).url
    )
  }

  def createForApiSubscribe(
      requestorName: String,
      requestorEmail: LaxEmailAddress,
      applicationName: String,
      applicationId: ApplicationId,
      apiName: String,
      apiVersion: ApiVersionNbr
    ): DeskproTicket = {
    val message = s"""I '${requestorEmail.text}' want my application '$applicationName'
                     |identified by '${applicationId}'
                     |to be subscribed to the API '$apiName'
                     |with version '${apiVersion.value}'""".stripMargin

    DeskproTicket(requestorName, requestorEmail, "Request to subscribe to an API", message, routes.SubscriptionsController.manageSubscriptions(applicationId).url)
  }

  def createForApiUnsubscribe(
      requestorName: String,
      requestorEmail: LaxEmailAddress,
      applicationName: String,
      applicationId: ApplicationId,
      apiName: String,
      apiVersion: ApiVersionNbr
    ): DeskproTicket = {
    val message = s"""I '${requestorEmail.text}' want my application '$applicationName'
                     |identified by '${applicationId}'
                     |to be unsubscribed from the API '$apiName'
                     |with version '${apiVersion.value}'""".stripMargin

    DeskproTicket(requestorName, requestorEmail, "Request to unsubscribe from an API", message, routes.SubscriptionsController.manageSubscriptions(applicationId).url)
  }

  def createForPrincipalApplicationDeletion(
      name: String,
      requestedByEmail: LaxEmailAddress,
      role: Collaborator.Role,
      environment: Environment,
      applicationName: String,
      applicationId: ApplicationId
    ): DeskproTicket = {

    val actor = role match {
      case Collaborator.Roles.ADMINISTRATOR => "an administrator"
      case _                                => "a developer"
    }

    val message =
      s"""I am $actor on the following ${environment.toString.toLowerCase} application '$applicationName'
         |and the application id is '${applicationId}'. I want it to be deleted from the Developer Hub.""".stripMargin

    DeskproTicket(name, requestedByEmail, "Request to delete an application", message, routes.DeleteApplication.deleteApplication(applicationId, None).url)
  }

  def createFromSupportEnquiry(supportEnquiry: SupportEnquiryForm, appTitle: String)(implicit request: Request[_]) = {

    val message =
      s"""${supportEnquiry.email} has submitted the following support enquiry:
         |
         |${supportEnquiry.comments}
         |
         |Please send them a response within 2 working days.
         |HMRC Developer Hub""".stripMargin
    DeskproTicket(
      name = supportEnquiry.fullname,
      email = supportEnquiry.email.toLaxEmail,
      subject = s"$appTitle: Support Enquiry",
      message = message,
      referrer = routes.Support.submitSupportEnquiry.url,
      userAgent = request.headers.get("User-Agent").getOrElse("n/a")
    )
  }

  def deleteDeveloperAccount(name: String, email: LaxEmailAddress): DeskproTicket = {
    val message =
      s"""I '${email.text}' want my Developer Hub account to be deleted"""

    DeskproTicket(name, email, "Request for developer account to be deleted", message, profile.routes.Profile.deleteAccount().url)
  }

  def removeDeveloper2SV(name: String, email: LaxEmailAddress): DeskproTicket = {
    val message =
      s"""I '${email.text}' want my 2SV to be removed"""

    DeskproTicket(name, email, "Request for 2SV to be removed", message, routes.UserLoginAccount.confirm2SVHelp().url)
  }

  def createForRequestChangeOfProductionApplicationName(
      requestorName: String,
      requestorEmail: LaxEmailAddress,
      previousApplicationName: String,
      newApplicationName: String,
      applicationId: ApplicationId
    ): DeskproTicket = {
    val message =
      s"""$requestorName wants to change the application name for $applicationId from $previousApplicationName to $newApplicationName.
         |Check if the new application name meets the naming guidelines and update Gatekeeper within 2 working days.
         |From HMRC Developer Hub
         |""".stripMargin

    DeskproTicket(
      requestorName,
      requestorEmail,
      "Change to application name",
      message,
      uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.routes.Details.requestChangeOfAppName(applicationId).url
    )
  }
}

sealed trait TicketResult

case object TicketCreated extends TicketResult
