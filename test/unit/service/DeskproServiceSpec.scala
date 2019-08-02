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

package unit.service

import config.ApplicationConfig
import connectors.DeskproConnector
import controllers.{SignOutSurveyForm, SupportEnquiryForm}
import domain.{DeskproTicket, Feedback, TicketCreated, TicketId}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatest.Matchers
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import play.api.test.FakeRequest
import service.DeskproService
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class DeskproServiceSpec extends UnitSpec with Matchers with MockitoSugar with ScalaFutures {
  val underTest = new DeskproService(
    mock[DeskproConnector],
    mock[ApplicationConfig]
  )

  "DeskproService" when {

    "A valid Signout Survey is completed" should {
      "Convert the SignoutSurveyForm into a Feedback Ticket and sends it to Deskpro" in {
        val title = "Title"
        when(underTest.appConfig.title).thenReturn(title)
        when(underTest.deskproConnector.createFeedback(any[Feedback])(any[HeaderCarrier])).thenReturn(Future(TicketId(123)))

        implicit val fakeRequest = FakeRequest()
        implicit val hc = HeaderCarrier()

        val form = SignOutSurveyForm(Some(5), "Nothing to report", "John Smith", "john@example.com", isJavascript = true)

        await(underTest.submitSurvey(form))

        val expectedData = Feedback.createFromSurvey(form, Some(title))
        verify(underTest.deskproConnector).createFeedback(expectedData)(hc)
      }

      "Convert the SignoutSurveyForm with empty suggested improvements into a Feedback Ticket and sends it to Deskpro" in {
        val title = "Title"
        when(underTest.appConfig.title).thenReturn(title)
        when(underTest.deskproConnector.createFeedback(any[Feedback])(any[HeaderCarrier])).thenReturn(Future(TicketId(123)))

        implicit val fakeRequest = FakeRequest()
        implicit val hc = HeaderCarrier()

        val form = SignOutSurveyForm(Some(5), "", "John Smith", "john@example.com", isJavascript = true)

        await(underTest.submitSurvey(form))

        val expectedData = Feedback.createFromSurvey(form, Some(title))
        expectedData.message shouldBe "n/a"
        verify(underTest.deskproConnector).createFeedback(expectedData)(hc)
      }
    }

    "A valid Support Enquiry is completed" should {
      "Convert the SupportEnquiryForm into a DeskproTicket and sends it to Deskpro" in {
        val title = "Title"
        when(underTest.appConfig.title).thenReturn(title)
        when(underTest.deskproConnector.createTicket(any[DeskproTicket])(any[HeaderCarrier])).thenReturn(Future(TicketCreated))

        implicit val fakeRequest = FakeRequest()
        implicit val hc = HeaderCarrier()

        val form = SupportEnquiryForm("my name", "myemail@example.com", "my comments")

        await(underTest.submitSupportEnquiry(form))

        val expectedData = DeskproTicket.createFromSupportEnquiry(form, title)
        verify(underTest.deskproConnector).createTicket(expectedData)(hc)
      }
    }
  }
}
