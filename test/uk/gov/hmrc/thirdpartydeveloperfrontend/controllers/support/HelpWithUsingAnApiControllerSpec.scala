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

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import views.html.support.HelpWithUsingAnApiView

import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.filters.csrf.CSRF.TokenProvider

import uk.gov.hmrc.apiplatform.modules.apis.domain.models.ApiDefinitionData
import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress.StringSyntax
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.DeveloperBuilder
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ErrorHandler
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.BaseControllerSpec
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.support._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.{LoggedInState, Session}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.flows.SupportFlow
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.service.{SessionServiceMock, SupportServiceMockModule}
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.DeskproService
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithLoggedInSession._
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.{LocalUserIdTracker, WithCSRFAddToken}

class HelpWithUsingAnApiControllerSpec extends BaseControllerSpec with WithCSRFAddToken with DeveloperBuilder with LocalUserIdTracker {
  val sessionId          = "sessionId"
  val apiServiceNameText = ApiDefinitionData.apiDefinition.serviceName.value

  trait Setup extends SessionServiceMock with SupportServiceMockModule {
    val helpWithUsingAnApiView = app.injector.instanceOf[HelpWithUsingAnApiView]

    val underTest = new HelpWithUsingAnApiController(
      mcc,
      cookieSigner,
      sessionServiceMock,
      mock[ErrorHandler],
      mock[DeskproService],
      SupportServiceMock.aMock,
      helpWithUsingAnApiView
    )

    val sessionParams: Seq[(String, String)] = Seq("csrfToken" -> app.injector.instanceOf[TokenProvider].generateToken)
    val developer                            = buildDeveloper(emailAddress = "thirdpartydeveloper@example.com".toLaxEmail)
    val basicFlow                            = SupportFlow(sessionId, "unknown")
    val appropriateFlow                      = basicFlow.copy(entrySelection = SupportData.UsingAnApi.id)

    def apiListShouldBeHidden(block: String)(implicit dom: Document) =
      dom.getElementById("conditional-" + block).classNames should contain("govuk-radios__conditional--hidden")

    def apiListShouldBeVisible(block: String)(implicit dom: Document) =
      dom.getElementById("conditional-" + block).classNames should not(contain("govuk-radios__conditional--hidden"))

    // Always find apis
    SupportServiceMock.FetchAllPublicApis.succeeds(List(ApiDefinitionData.apiDefinition))

    def shouldBeRedirectedToPreviousPage(result: Future[Result]) = {
      status(result) shouldBe SEE_OTHER
      redirectLocation(result).value shouldBe "/developer/new-support"
    }

    def shouldBeRedirectedToDetailsPage(result: Future[Result]) = {
      status(result) shouldBe SEE_OTHER
      redirectLocation(result).value shouldBe "/developer/new-support/details"
    }

    def shouldBeRedirectedToChoosePrivateApiPage(result: Future[Result]) = {
      status(result) shouldBe SEE_OTHER
      redirectLocation(result).value shouldBe "/developer/new-support/api/private-api"
    }
  }

  trait IsLoggedIn {
    self: Setup =>

    val request = FakeRequest()
      .withLoggedIn(underTest, cookieSigner)(sessionId)
      .withSession(sessionParams: _*)

    fetchSessionByIdReturns(sessionId, Session(sessionId, developer, LoggedInState.LOGGED_IN))
  }

  trait NoSupportSessionExists {
    self: Setup =>

    val request = FakeRequest()
      .withLoggedIn(underTest, cookieSigner)(sessionId)
      .withSession(sessionParams: _*)

    fetchSessionByIdReturnsNone(sessionId)
  }

  trait NotLoggedIn {
    self: Setup =>

    val request = FakeRequest()
      .withSession(sessionParams: _*)

    fetchSessionByIdReturnsNone(sessionId)
  }

  trait IsPartLoggedInEnablingMFA {
    self: Setup =>

    val request = FakeRequest()
      .withLoggedIn(underTest, cookieSigner)(sessionId)
      .withSession(sessionParams: _*)

    fetchSessionByIdReturns(sessionId, Session(sessionId, developer, LoggedInState.PART_LOGGED_IN_ENABLING_MFA))
  }

  "HelpWithUsingAnApiController" when {
    "using the delta functions" should {
      "work for chooseMakingCall" in {
        val form = HelpWithUsingAnApiForm("ignored", apiNameForCall = apiServiceNameText, "ignored", "ignored")
        val flow = SupportFlow(sessionId, "untouched")

        val result = HelpWithUsingAnApiController.chooseMakingCall(form)(flow)

        result.entrySelection shouldBe "untouched"
        result.subSelection.value shouldBe SupportData.MakingAnApiCall.id
        result.api.value shouldBe apiServiceNameText
      }

      "work for chooseGettingExamples" in {
        val form = HelpWithUsingAnApiForm("ignored", "ignored", apiNameForExamples = apiServiceNameText, "ignored")
        val flow = SupportFlow(sessionId, "untouched")

        val result = HelpWithUsingAnApiController.chooseGettingExamples(form)(flow)

        result.entrySelection shouldBe "untouched"
        result.subSelection.value shouldBe SupportData.GettingExamples.id
        result.api.value shouldBe apiServiceNameText
      }

      "work for chooseReporting" in {
        val form = HelpWithUsingAnApiForm("ignored", "ignored", "ignored", apiNameForReporting = apiServiceNameText)
        val flow = SupportFlow(sessionId, "untouched")

        val result = HelpWithUsingAnApiController.chooseReporting(form)(flow)

        result.entrySelection shouldBe "untouched"
        result.subSelection.value shouldBe SupportData.ReportingDocumentation.id
        result.api.value shouldBe apiServiceNameText
      }

      "work for choosePrivateApi" in {
        val form = HelpWithUsingAnApiForm("ignored", "ignored", "ignored", "ignored")
        val flow = SupportFlow(sessionId, "untouched")

        val result = HelpWithUsingAnApiController.choosePrivateApi(form)(flow)

        result.entrySelection shouldBe "untouched"
        result.subSelection.value shouldBe SupportData.PrivateApiDocumentation.id
        result.api shouldBe None
      }
    }

    "invoke page" should {
      "render the helpWithUsingAnApi page when flow is appropriate" in new Setup with IsLoggedIn {
        SupportServiceMock.GetSupportFlow.succeeds(appropriateFlow)

        val result = addToken(underTest.page())(request)

        status(result) shouldBe OK
        implicit val dom: Document = Jsoup.parse(contentAsString(result))
        apiListShouldBeHidden(SupportData.MakingAnApiCall.id)
        apiListShouldBeHidden(SupportData.GettingExamples.id)
        apiListShouldBeHidden(SupportData.ReportingDocumentation.id)
      }

      "render the previous page when flow is not appropriate" in new Setup with IsLoggedIn {
        SupportServiceMock.GetSupportFlow.succeeds(basicFlow)

        val result = addToken(underTest.page())(request)

        shouldBeRedirectedToPreviousPage(result)
      }

      "render the previous page when there is no flow" in new Setup with NoSupportSessionExists {
        SupportServiceMock.GetSupportFlow.succeeds(basicFlow.copy(privateApi = None))

        val result = addToken(underTest.page())(request)

        shouldBeRedirectedToPreviousPage(result)
      }
    }

    "invoke submit" should {
      "handle option 'Making an API call'" in new Setup with IsLoggedIn {
        SupportServiceMock.GetSupportFlow.succeeds(appropriateFlow)
        SupportServiceMock.UpdateWithDelta.succeeds()

        val formRequest = request
          .withFormUrlEncodedBody(
            "choice"                                            -> SupportData.MakingAnApiCall.id,
            SupportData.MakingAnApiCall.id + "-api-name"        -> apiServiceNameText,
            SupportData.GettingExamples.id + "-api-name"        -> "ignored",
            SupportData.ReportingDocumentation.id + "-api-name" -> "ignored"
          )

        val result = addToken(underTest.submit())(formRequest)

        shouldBeRedirectedToDetailsPage(result)
      }

      "handle option 'Getting examples for an API'" in new Setup with IsLoggedIn {
        SupportServiceMock.GetSupportFlow.succeeds(appropriateFlow)
        SupportServiceMock.UpdateWithDelta.succeeds()

        val formRequest = request
          .withFormUrlEncodedBody(
            "choice"                                            -> SupportData.GettingExamples.id,
            SupportData.MakingAnApiCall.id + "-api-name"        -> "ignored",
            SupportData.GettingExamples.id + "-api-name"        -> apiServiceNameText,
            SupportData.ReportingDocumentation.id + "-api-name" -> "ignored"
          )

        val result = addToken(underTest.submit())(formRequest)
        shouldBeRedirectedToDetailsPage(result)
      }

      "handle option 'Reporting documentation for an API'" in new Setup with IsLoggedIn {
        SupportServiceMock.GetSupportFlow.succeeds(appropriateFlow)
        SupportServiceMock.UpdateWithDelta.succeeds()

        val formRequest = request
          .withFormUrlEncodedBody(
            "choice"                                            -> SupportData.ReportingDocumentation.id,
            SupportData.MakingAnApiCall.id + "-api-name"        -> "ignored",
            SupportData.GettingExamples.id + "-api-name"        -> "ignored",
            SupportData.ReportingDocumentation.id + "-api-name" -> apiServiceNameText
          )

        val result = addToken(underTest.submit())(formRequest)

        shouldBeRedirectedToDetailsPage(result)
      }

      "handle option 'Private API Documentation'" in new Setup with IsLoggedIn {
        SupportServiceMock.GetSupportFlow.succeeds(appropriateFlow)
        SupportServiceMock.UpdateWithDelta.succeeds()

        val formRequest = request
          .withFormUrlEncodedBody(
            "choice"                                            -> SupportData.PrivateApiDocumentation.id,
            SupportData.MakingAnApiCall.id + "-api-name"        -> "ignored",
            SupportData.GettingExamples.id + "-api-name"        -> "ignored",
            SupportData.ReportingDocumentation.id + "-api-name" -> "ignored"
          )

        val result = addToken(underTest.submit())(formRequest)

        shouldBeRedirectedToChoosePrivateApiPage(result)
      }

      "handle bad request" in new Setup with IsLoggedIn {
        SupportServiceMock.GetSupportFlow.succeeds(appropriateFlow)

        val formRequest = request.withFormUrlEncodedBody("choice" -> "random stuff")

        val result = addToken(underTest.submit())(formRequest)

        status(result) shouldBe BAD_REQUEST
      }

      "submit valid request but no session" in new Setup with NoSupportSessionExists {
        val formRequest = request.withFormUrlEncodedBody(
          "choice"                                            -> SupportData.ReportingDocumentation.id,
          SupportData.MakingAnApiCall.id + "-api-name"        -> "ignored",
          SupportData.GettingExamples.id + "-api-name"        -> "ignored",
          SupportData.ReportingDocumentation.id + "-api-name" -> apiServiceNameText
        )

        SupportServiceMock.GetSupportFlow.succeeds(basicFlow)

        val result = addToken(underTest.submit())(formRequest)

        shouldBeRedirectedToPreviousPage(result)
      }

    }
  }
}
