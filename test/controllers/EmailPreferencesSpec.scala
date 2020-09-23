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

import domain.models.apidefinitions.ApiContext
import domain.models.connectors.ExtendedAPIDefinition
import service.APIService
import views.html.emailpreferences.EmailPreferencesSummaryView

import scala.concurrent.Future
import domain.models.developers.{Developer, DeveloperSession, LoggedInState, Session}
import play.api.test.FakeRequest
import utils.WithLoggedInSession._
import play.api.mvc.AnyContentAsEmpty
import play.filters.csrf.CSRF.TokenProvider
import play.api.test.Helpers._
import views.emailpreferences.EmailPreferencesSummaryViewData
import model.EmailPreferences

import scala.concurrent.ExecutionContext.Implicits.global
import model.APICategoryDetails
import mocks.service.SessionServiceMock
import model.TaxRegimeInterests

class EmailPreferencesSpec extends BaseControllerSpec with SessionServiceMock {
  
    trait Setup {
        val mockAPIService = mock[APIService]

        val mockEmailPreferencesSummaryView = mock[EmailPreferencesSummaryView]
        when(mockEmailPreferencesSummaryView.apply(*)(*,*,*,*)).thenReturn(play.twirl.api.HtmlFormat.empty)

        val controllerUnderTest = new EmailPreferences(sessionServiceMock, mcc, mockErrorHandler, cookieSigner, mockAPIService, mockEmailPreferencesSummaryView)
    
        val emailPreferences = EmailPreferences(List(TaxRegimeInterests("CATEGORY_1", Set("api1", "api2"))), Set.empty)
        val developer: Developer = Developer("third.party.developer@example.com", "John", "Doe", emailPreferences = emailPreferences)
        val sessionId = "sessionId"
        val session: Session = Session(sessionId, developer, LoggedInState.LOGGED_IN)
        val loggedInDeveloper: DeveloperSession = DeveloperSession(session)
        private val sessionParams = Seq("csrfToken" -> app.injector.instanceOf[TokenProvider].generateToken)

        val loggedInRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest().withLoggedIn(controllerUnderTest, implicitly)(sessionId).withSession(sessionParams: _*)
    }

    "emailPreferencesSummaryPage" should {
        val mockCategory1 = APICategoryDetails("CATEGORY_1", "Category 1")
        val mockCategory2 = APICategoryDetails("CATEGORY_2", "Category 2")
        val apiCategoryDetails: Seq[APICategoryDetails] = Seq(mockCategory1, mockCategory2)
        val api1 = ExtendedAPIDefinition("api1", "API 1", "desc", ApiContext("CATEGORY_1"))
        val api2 = ExtendedAPIDefinition("api2", "API 2", "desc2", ApiContext("CATEGORY_1"))
        val apis = Set(api1.serviceName, api2.serviceName)
        val fetchedAPis = Seq(api1, api2)

        "return emailPreferencesSummaryView page for logged in user" in new Setup {
            val expectedCategoryMap = Map("CATEGORY_1" -> "Category 1")
            fetchSessionByIdReturns(sessionId, session)

            val expectedAPIDisplayNames = Map(api1.serviceName -> api1.name, api2.serviceName-> api2.name)

            when(mockAPIService.fetchAllAPICategoryDetails()(*)).thenReturn(Future.successful(apiCategoryDetails))
            when(mockAPIService.fetchAPIDetails(eqTo(apis))(*)).thenReturn(Future.successful(fetchedAPis))

            val result = controllerUnderTest.emailPreferencesSummaryPage()(loggedInRequest)

            status(result) shouldBe OK

            verify(mockEmailPreferencesSummaryView).apply(eqTo(EmailPreferencesSummaryViewData(expectedCategoryMap, expectedAPIDisplayNames)))(*,*,*,*)
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
