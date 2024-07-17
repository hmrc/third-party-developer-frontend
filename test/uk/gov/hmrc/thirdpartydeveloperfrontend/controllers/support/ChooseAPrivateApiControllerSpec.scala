/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.support

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import views.html.support._

import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers._

import uk.gov.hmrc.apiplatform.modules.tpd.builder.UserBuilder
import uk.gov.hmrc.apiplatform.modules.tpd.utils.LocalUserIdTracker
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ErrorHandler
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.BaseControllerSpec
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.SupportSessionId
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.flows.SupportFlow
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.service.{SessionServiceMock, SupportServiceMockModule}
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.DeskproService
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithCSRFAddToken
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithLoggedInSession._

class ChooseAPrivateApiControllerSpec extends BaseControllerSpec with WithCSRFAddToken with UserBuilder with LocalUserIdTracker {

  trait Setup extends SessionServiceMock with SupportServiceMockModule {
    val checkCdsAccessIsRequiredView = app.injector.instanceOf[CheckCdsAccessIsRequiredView]
    val chooseAPrivateApiView        = app.injector.instanceOf[ChooseAPrivateApiView]

    lazy val request = FakeRequest()
      .withSupport(underTest, cookieSigner)(supportSessionId)

    fetchSessionByIdReturnsNone()

    val underTest = new ChooseAPrivateApiController(
      mcc,
      cookieSigner,
      sessionServiceMock,
      mock[ErrorHandler],
      mock[DeskproService],
      SupportServiceMock.aMock,
      chooseAPrivateApiView,
      checkCdsAccessIsRequiredView
    )

    val supportSessionId = SupportSessionId.random
    val basicFlow        = SupportFlow(supportSessionId, "?")
    val appropriateFlow  = basicFlow.copy(entrySelection = SupportData.UsingAnApi.id, subSelection = Some(SupportData.PrivateApiDocumentation.id))

    def shouldBeRedirectedToPreviousPage(result: Future[Result]) = {
      status(result) shouldBe SEE_OTHER
      redirectLocation(result).value shouldBe "/developer/new-support/api/choose-api"
    }

    def shouldBeRedirectedToApplyPage(result: Future[Result]) = {
      status(result) shouldBe SEE_OTHER
      redirectLocation(result).value shouldBe "/developer/new-support/api/private-api/apply"
    }

    def shouldBeRedirectedToConfirmCdsPage(result: Future[Result]) = {
      status(result) shouldBe SEE_OTHER
      redirectLocation(result).value shouldBe "/developer/new-support/api/private-api/cds-check"
    }
  }

  "ChooseAPrivateApiController" when {
    "invoke chooseAPrivateApiPage" should {
      "render the page when flow is correct" in new Setup {
        SupportServiceMock.GetSupportFlow.succeeds(appropriateFlow)

        val result = addToken(underTest.page())(request)

        status(result) shouldBe OK
      }

      "render the previous page when flow is wrong" in new Setup {
        SupportServiceMock.GetSupportFlow.succeeds(basicFlow.copy(entrySelection = SupportData.UsingAnApi.id))

        val result = addToken(underTest.page())(request)

        shouldBeRedirectedToPreviousPage(result)
      }

      "render the previous page when there is no flow" in new Setup {
        SupportServiceMock.GetSupportFlow.succeeds(basicFlow)

        val result = addToken(underTest.page())(request)

        shouldBeRedirectedToPreviousPage(result)
      }
    }

    "invoke submitChoiceOfPrivateApi" should {
      "submit new valid request from form for business rates choice" in new Setup {
        val formRequest = request.withFormUrlEncodedBody(
          "apiName" -> SupportData.ChooseBusinessRates.id
        )
        SupportServiceMock.GetSupportFlow.succeeds(appropriateFlow)
        SupportServiceMock.UpdateWithDelta.succeeds()

        val result = addToken(underTest.submit())(formRequest)

        shouldBeRedirectedToApplyPage(result)
      }

      "submit new valid request from form for CDS choice" in new Setup {
        val formRequest = request.withFormUrlEncodedBody(
          "apiName" -> SupportData.ChooseCDS.id
        )
        SupportServiceMock.GetSupportFlow.succeeds(appropriateFlow)
        SupportServiceMock.UpdateWithDelta.succeeds()

        val result = addToken(underTest.submit())(formRequest)

        shouldBeRedirectedToConfirmCdsPage(result)
      }

      "submit invalid request returns BAD_REQUEST" in new Setup {
        val formRequest = request.withFormUrlEncodedBody(
          "bobbins" -> SupportData.ChooseBusinessRates.id
        )
        SupportServiceMock.GetSupportFlow.succeeds(appropriateFlow)

        val result = addToken(underTest.submit())(formRequest)

        status(result) shouldBe BAD_REQUEST
      }

      "submit valid request but no session" in new Setup {
        val formRequest = request.withFormUrlEncodedBody(
          "apiName" -> SupportData.ChooseCDS.id
        )
        SupportServiceMock.GetSupportFlow.succeeds()

        val result = addToken(underTest.submit())(formRequest)

        shouldBeRedirectedToPreviousPage(result)
      }
    }
  }
}
