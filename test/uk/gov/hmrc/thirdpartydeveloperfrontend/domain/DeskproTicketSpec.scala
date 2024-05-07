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

package uk.gov.hmrc.thirdpartydeveloperfrontend.domain

import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest

import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress.StringSyntax
import uk.gov.hmrc.apiplatform.modules.common.domain.models._
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.support.SupportEnquiryForm
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors.DeskproTicket
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.AsyncHmrcSpec

class DeskproTicketSpec extends AsyncHmrcSpec {

  implicit val fakeRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()

  val requestorName   = "bob example"
  val requestorEmail  = "bob@example.com".toLaxEmail
  val applicationName = "my app"
  val applicationId   = ApplicationId.random
  val apiName         = "my api"
  val apiVersion      = ApiVersionNbr.random
  val comments        = "very nice"

  def checkDeskproTicket(ticket: DeskproTicket, expectedSubject: String, expectedMsg: String) = {
    ticket.message.replaceAll("\\s+", " ") shouldBe expectedMsg
    ticket.name shouldBe requestorName
    ticket.email shouldBe requestorEmail
  }

  "A DeskproTicket created from a support enquiry" should {
    "have the correct details populated" in {
      val form = SupportEnquiryForm(requestorName, requestorEmail.text, comments)

      val ticket = DeskproTicket.createFromSupportEnquiry(form, applicationName)

      checkDeskproTicket(
        ticket,
        s"$applicationName: Support Enquiry",
        s"${requestorEmail.text} has submitted the following support enquiry: $comments Please send them a response within 2 working days. HMRC Developer Hub"
      )
    }
  }

  "a deskpro ticket created for Api Subscribe" should {
    "have the correct details populated" in {
      val ticket = DeskproTicket.createForApiSubscribe(requestorName, requestorEmail, applicationName, applicationId, apiName, apiVersion)

      checkDeskproTicket(
        ticket,
        "Request to subscribe to an API",
        s"I '${requestorEmail.text}' want my application '$applicationName' identified by '${applicationId}' to be subscribed to the API '$apiName' with version '${apiVersion.value}'"
      )
    }
  }

  "a deskpro ticket created for Api unsubscribe" should {
    "have the correct details populated" in {
      val ticket = DeskproTicket.createForApiUnsubscribe(requestorName, requestorEmail, applicationName, applicationId, apiName, apiVersion)

      checkDeskproTicket(
        ticket,
        "Request to unsubscribe from an API",
        s"I '${requestorEmail.text}' want my application '$applicationName' identified by '${applicationId}' to be unsubscribed from the API '$apiName' with version '${apiVersion.value}'"
      )
    }
  }
}
