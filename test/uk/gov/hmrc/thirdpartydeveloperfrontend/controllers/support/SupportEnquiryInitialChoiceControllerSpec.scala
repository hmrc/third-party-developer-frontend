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

package uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.support

import scala.concurrent.ExecutionContext.Implicits.global

import org.jsoup.Jsoup
import views.html.support.SupportEnquiryInitialChoiceView

import play.api.test.FakeRequest
import play.api.test.Helpers._

import uk.gov.hmrc.apiplatform.modules.apis.domain.models.ApiDefinitionData
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

class SupportEnquiryInitialChoiceControllerSpec extends BaseControllerSpec with WithCSRFAddToken with UserBuilder with LocalUserIdTracker {

  trait Setup extends SessionServiceMock with SupportServiceMockModule {
    val supportEnquiryInitialChoiceView = app.injector.instanceOf[SupportEnquiryInitialChoiceView]

    lazy val request = FakeRequest()
      .withSupport(underTest, cookieSigner)(supportSessionId)

    fetchSessionByIdReturnsNone()

    val underTest = new SupportEnquiryInitialChoiceController(
      mcc,
      cookieSigner,
      sessionServiceMock,
      mock[ErrorHandler],
      mock[DeskproService],
      SupportServiceMock.aMock,
      supportEnquiryInitialChoiceView
    )

    val supportSessionId = SupportSessionId.random
    val appropriateFlow  = SupportFlow(supportSessionId, SupportData.SigningIn.id)

    SupportServiceMock.FetchAllPublicApis.succeeds(List(ApiDefinitionData.apiDefinition))

  }

  "SupportEnquiryController" when {
    "invoking page for new support" should {
      "render the new support enquiry initial choice page" in new Setup {
        val result = addToken(underTest.page())(request)

        status(result) shouldBe OK
        val dom = Jsoup.parse(contentAsString(result))

        dom.getElementById(SupportData.FindingAnApi.id).attr("value") shouldEqual SupportData.FindingAnApi.id
        dom.getElementById(SupportData.UsingAnApi.id).attr("value") shouldEqual SupportData.UsingAnApi.id
        dom.getElementById(SupportData.SigningIn.id).attr("value") shouldEqual SupportData.SigningIn.id
        dom.getElementById(SupportData.SettingUpApplication.id).attr("value") shouldEqual SupportData.SettingUpApplication.id
      }

    }

    "invovking submit" should {
      "redirect to the new help with using an api page when the 'Using an API' option is selected" in new Setup {
        val formRequest = request
          .withFormUrlEncodedBody("initialChoice" -> SupportData.UsingAnApi.id)

        val result = addToken(underTest.submit())(formRequest)

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some("/developer/new-support/api/choose-api")
      }

      "redirect to the generic support details page when any other option is selected" in new Setup {
        val formRequest = request
          .withFormUrlEncodedBody("initialChoice" -> SupportData.FindingAnApi.id)

        val result = addToken(underTest.submit())(formRequest)

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some("/developer/new-support/details")
      }

      "submit invalid request returns BAD_REQUEST" in new Setup {
        val formRequest = request
          .withFormUrlEncodedBody(
            "xyz" -> "blah"
          )

        SupportServiceMock.GetSupportFlow.succeeds(appropriateFlow)

        val result = addToken(underTest.submit())(formRequest)

        redirectLocation(result) shouldBe None
        status(result) shouldBe BAD_REQUEST
      }

    }
  }
}
