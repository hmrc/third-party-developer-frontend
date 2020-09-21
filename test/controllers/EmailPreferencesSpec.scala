/*
 * Copyright 2020 HM Revenue & Customs
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

package controllers

import service.APIService
import views.html.emailpreferences.EmailPreferencesSummaryView
import scala.concurrent.Future
import domain.models.developers.{Developer, DeveloperSession, LoggedInState, Session}
import play.api.test.FakeRequest
import utils.WithLoggedInSession._
import play.api.mvc.AnyContentAsEmpty
import play.filters.csrf.CSRF.TokenProvider
import play.api.test.Helpers._

import scala.concurrent.ExecutionContext.Implicits.global
import model.APICategoryDetails
import mocks.service.SessionServiceMock

class EmailPreferencesSpec extends BaseControllerSpec with SessionServiceMock {
  
    trait Setup {
        val mockAPIService = mock[APIService]

        val mockEmailPreferencesSummaryView = mock[EmailPreferencesSummaryView]
        when(mockEmailPreferencesSummaryView.apply(*)(*,*,*,*)).thenReturn(play.twirl.api.HtmlFormat.empty)

        val controllerUnderTest = new EmailPreferences(sessionServiceMock, mcc, mockErrorHandler, cookieSigner, mockAPIService, mockEmailPreferencesSummaryView)
    
        val developer: Developer = Developer("third.party.developer@example.com", "John", "Doe")
        val sessionId = "sessionId"
        val session: Session = Session(sessionId, developer, LoggedInState.LOGGED_IN)
        val loggedInDeveloper: DeveloperSession = DeveloperSession(session)
        private val sessionParams = Seq("csrfToken" -> app.injector.instanceOf[TokenProvider].generateToken)

        val loggedInRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest().withLoggedIn(controllerUnderTest, implicitly)(sessionId).withSession(sessionParams: _*)
    }

    "emailPreferencesSummaryPage" should {
        val mockCategory1 = mock[APICategoryDetails]
        val mockCategory2 = mock[APICategoryDetails]

        "return emailPreferencesSummaryView page for logged in user" in new Setup {
            fetchSessionByIdReturns(sessionId, session)

        
            val apiCategoryDetails: Seq[APICategoryDetails] = Seq(mockCategory1, mockCategory2)
            when(mockAPIService.fetchAllAPICategoryDetails()(*)).thenReturn(Future.successful(apiCategoryDetails))

            val result = controllerUnderTest.emailPreferencesSummaryPage()(loggedInRequest)

            status(result) shouldBe OK
            verify(mockEmailPreferencesSummaryView).apply(eqTo(apiCategoryDetails))(*,*,*,*)
        }

        "redirect to login screen for non-logged in user" in new Setup {
            fetchSessionByIdReturnsNone(sessionId)

            val result = controllerUnderTest.emailPreferencesSummaryPage()(FakeRequest())

            status(result) shouldBe SEE_OTHER
            redirectLocation(result) shouldBe Some(controllers.routes.UserLoginAccount.login().url)

            verifyZeroInteractions(mockAPIService)
            verifyZeroInteractions(mockEmailPreferencesSummaryView)
        }
    }
}