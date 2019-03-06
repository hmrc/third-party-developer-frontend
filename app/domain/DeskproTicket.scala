/*
 * Copyright 2019 HM Revenue & Customs
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

package domain

import controllers.{SupportEnquiryForm, routes}
import play.api.libs.json.Json
import play.api.mvc.Request

case class DeskproTicket(name: String,
                         email: String,
                         subject: String,
                         message: String,
                         referrer: String,
                         userAgent: String = "",
                         authId: String = "",
                         areaOfTax: String = "",
                         sessionId: String = "",
                         javascriptEnabled: String = "")

object DeskproTicket extends FieldTransformer {
  implicit val format = Json.format[DeskproTicket]

  def createForUplift(requestorName: String, requestorEmail: String, applicationName: String, applicationId: String): DeskproTicket = {
    val message =
      s"""$requestorEmail submitted the following application for production use on the Developer Hub:
         |$applicationName
         |Please check it against our guidelines and send them a response within 2 working days.
         |HMRC Developer Hub
         |""".stripMargin

    DeskproTicket(requestorName, requestorEmail, "New application submitted for checking", message, routes.ApplicationCheck.requestCheckPage(applicationId).url)
  }

  def createForApiSubscribe(requestorName: String, requestorEmail: String,
                            applicationName: String, applicationId: String,
                            apiName: String, apiVersion: String): DeskproTicket = {
    val message = s"""I '$requestorEmail' want my application '$applicationName'
                  |identified by '$applicationId'
                  |to be subscribed to the API '$apiName'
                  |with version '$apiVersion'""".stripMargin

    DeskproTicket(requestorName, requestorEmail, "Request to subscribe to an API", message, routes.Subscriptions.subscriptions(applicationId).url)
  }

  def createForApiUnsubscribe(requestorName: String, requestorEmail: String,
                              applicationName: String, applicationId: String,
                              apiName: String, apiVersion: String): DeskproTicket = {
    val message = s""""I '$requestorEmail' want my application '$applicationName'
                  |identified by '$applicationId'
                  |to be unsubscribed from the API '$apiName'
                  |with version '$apiVersion'""".stripMargin

    DeskproTicket(requestorName, requestorEmail, "Request to unsubscribe from an API", message, routes.Subscriptions.subscriptions(applicationId).url)
  }

  def createForApplicationDeletion(name: String, requestedByEmail: String, role: Role, environment: Environment,
                      applicationName: String, applicationId: String): DeskproTicket = {

    val actor = role match {
      case Role.ADMINISTRATOR => "an administrator"
      case _ => "a developer"
    }

    val message =
      s"""I am $actor on the following ${environment.toString.toLowerCase} application '$applicationName'
         |and the application id is '$applicationId'. I want it to be deleted from the Developer Hub.""".stripMargin

    DeskproTicket(name, requestedByEmail, "Request to delete an application",
      message, routes.DeleteApplication.deleteApplication(applicationId, None).url)
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
      email = supportEnquiry.email,
      subject = s"$appTitle: Support Enquiry",
      message = message,
      referrer = routes.Support.submitSupportEnquiry().url,
      userAgent = request.headers.get("User-Agent").getOrElse("n/a"))
  }

  def deleteDeveloperAccount(name: String, email: String): DeskproTicket = {
    val message =
      s"""I '$email' want my Developer Hub account to be deleted"""

    DeskproTicket(name, email, "Request for developer account to be deleted", message, routes.Profile.deleteAccount().url)
  }
  def removeDeveloper2SV(email: String): DeskproTicket = {
    val message =
      s"""I '$email' want my 2SV to be removed"""

    DeskproTicket("", email, "Request for 2SV to be removed", message, routes.UserLoginAccount.confirm2SVHelp().url)
  }


}

sealed trait TicketResult

case object TicketCreated extends TicketResult
