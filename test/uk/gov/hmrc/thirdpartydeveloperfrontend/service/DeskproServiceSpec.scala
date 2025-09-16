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

package uk.gov.hmrc.thirdpartydeveloperfrontend.service

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import play.api.test.FakeRequest
import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ApplicationConfig
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.DeskproConnector
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.SignOutSurveyForm
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors.{Feedback, TicketId}
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.AsyncHmrcSpec

class DeskproServiceSpec extends AsyncHmrcSpec {

  val underTest = new DeskproService(
    mock[DeskproConnector],
    mock[ApplicationConfig]
  )

  "DeskproService" when {

    "A valid Signout Survey is completed" should {
      "Convert the SignoutSurveyForm into a Feedback Ticket and sends it to Deskpro" in {
        val title = "Title"
        when(underTest.appConfig.title).thenReturn(title)
        when(underTest.deskproConnector.createFeedback(any[Feedback])(*)).thenReturn(Future(TicketId(123)))

        implicit val fakeRequest = FakeRequest()
        implicit val hc          = HeaderCarrier()

        val form = SignOutSurveyForm(Some(5), "Nothing to report", "John Smith", "john@example.com", isJavascript = true)

        await(underTest.submitSurvey(form))

        val expectedData = Feedback.createFromSurvey(form, Some(title))
        verify(underTest.deskproConnector).createFeedback(expectedData)(hc)
      }

      "Convert the SignoutSurveyForm with empty suggested improvements into a Feedback Ticket and sends it to Deskpro" in {
        val title = "Title"
        when(underTest.appConfig.title).thenReturn(title)
        when(underTest.deskproConnector.createFeedback(any[Feedback])(*)).thenReturn(Future(TicketId(123)))

        implicit val fakeRequest = FakeRequest()
        implicit val hc          = HeaderCarrier()

        val form = SignOutSurveyForm(Some(5), "", "John Smith", "john@example.com", isJavascript = true)

        await(underTest.submitSurvey(form))

        val expectedData = Feedback.createFromSurvey(form, Some(title))
        expectedData.message shouldBe "n/a"
        verify(underTest.deskproConnector).createFeedback(expectedData)(hc)
      }
    }
  }
}
