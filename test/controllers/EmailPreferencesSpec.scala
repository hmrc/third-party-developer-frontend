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
import domain.models.connectors.{ApiDefinition, ExtendedApiDefinition}
import service.{APIService, EmailPreferencesService}
import views.html.emailpreferences._

import scala.concurrent.Future
import domain.models.developers.{Developer, DeveloperSession, LoggedInState, Session}
import domain.models.flows.EmailPreferencesFlow
import play.api.test.FakeRequest
import utils.WithLoggedInSession._
import play.api.mvc.AnyContentAsEmpty
import play.filters.csrf.CSRF.TokenProvider
import play.api.test.Helpers._
import views.emailpreferences.EmailPreferencesSummaryViewData

import scala.concurrent.ExecutionContext.Implicits.global
import model.APICategoryDetails
import mocks.service.SessionServiceMock
import model.TaxRegimeInterests
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import config.ApplicationConfig
import mocks.service.ErrorHandlerMock
import play.api.libs.crypto.CookieSigner
import play.api.i18n.MessagesApi
import play.api.mvc.MessagesControllerComponents

class EmailPreferencesSpec extends PlaySpec with GuiceOneAppPerSuite with SessionServiceMock with ErrorHandlerMock {

  trait Setup {
    val mockEmailPreferencesService = mock[EmailPreferencesService]
    val mockAPIService = mock[APIService]


  implicit val cookieSigner: CookieSigner = app.injector.instanceOf[CookieSigner]

  implicit lazy val materializer = app.materializer

  lazy val messagesApi = app.injector.instanceOf[MessagesApi]

  val mcc = app.injector.instanceOf[MessagesControllerComponents]

  implicit val appConfig: ApplicationConfig = mock[ApplicationConfig]
  when(appConfig.nameOfPrincipalEnvironment).thenReturn("Production")
  when(appConfig.nameOfSubordinateEnvironment).thenReturn("Sandbox")

    val mockEmailPreferencesSummaryView = mock[EmailPreferencesSummaryView]
    val mockEmailPreferencesUnsubscribeAllView = mock[EmailPreferencesUnsubscribeAllView]
    val mockEmailPreferencesStartView = mock[FlowStartView]
    val mockEmailPreferencesSelectCategoriesView = mock[FlowSelectCategoriesView]
    val mockEmailPreferencesFlowSelectTopicView = mock[FlowSelectTopicsView]
     val mockEmailPreferencesSelectApiView = mock[FlowSelectApiView]
    when(mockEmailPreferencesSummaryView.apply(*)(*, *, *, *)).thenReturn(play.twirl.api.HtmlFormat.empty)
    when(mockEmailPreferencesUnsubscribeAllView.apply()(*, *, *, *)).thenReturn(play.twirl.api.HtmlFormat.empty)
    when(mockEmailPreferencesStartView.apply()(*, *, *, *)).thenReturn(play.twirl.api.HtmlFormat.empty)
    when(mockEmailPreferencesSelectCategoriesView.apply(*, *)(*, *, *, *)).thenReturn(play.twirl.api.HtmlFormat.empty)
    when(mockEmailPreferencesFlowSelectTopicView.apply(*)(*, *, *, *)).thenReturn(play.twirl.api.HtmlFormat.empty)
    when(mockEmailPreferencesSelectApiView.apply(*, *, *)(*, *, *, *)).thenReturn(play.twirl.api.HtmlFormat.empty)
    
    val controllerUnderTest =
      new EmailPreferences(
        sessionServiceMock,
        mcc,
        mockErrorHandler,
        cookieSigner,
        mockEmailPreferencesService,
        mockAPIService,
        mockEmailPreferencesSummaryView,
        mockEmailPreferencesUnsubscribeAllView,
        mockEmailPreferencesStartView,
        mockEmailPreferencesSelectCategoriesView,
        mockEmailPreferencesSelectApiView,
        mockEmailPreferencesFlowSelectTopicView
        )

    val emailPreferences = model.EmailPreferences(List(TaxRegimeInterests("CATEGORY_1", Set("api1", "api2"))), Set.empty)
    val developer: Developer = Developer("third.party.developer@example.com", "John", "Doe")
    val developerWithEmailPrefences: Developer = developer.copy(emailPreferences = emailPreferences)
    val sessionId = "sessionId"
    val session: Session = Session(sessionId, developerWithEmailPrefences, LoggedInState.LOGGED_IN)
    val sessionNoEMailPrefences: Session = Session(sessionId, developer, LoggedInState.LOGGED_IN)
    val loggedInDeveloper: DeveloperSession = DeveloperSession(session)
    private val sessionParams = Seq("csrfToken" -> app.injector.instanceOf[TokenProvider].generateToken)

    val loggedInRequest: FakeRequest[AnyContentAsEmpty.type] =
      FakeRequest().withLoggedIn(controllerUnderTest, implicitly)(sessionId).withSession(sessionParams: _*)
  }

  "emailPreferencesSummaryPage" should {
    val mockCategory1 = APICategoryDetails("CATEGORY_1", "Category 1")
    val mockCategory2 = APICategoryDetails("CATEGORY_2", "Category 2")
    val apiCategoryDetails: Seq[APICategoryDetails] = Seq(mockCategory1, mockCategory2)
    val api1 = ExtendedApiDefinition("api1", "API 1", "desc", ApiContext("CATEGORY_1"))
    val api2 = ExtendedApiDefinition("api2", "API 2", "desc2", ApiContext("CATEGORY_1"))
    val apis = Set(api1.serviceName, api2.serviceName)
    val fetchedAPis = Seq(api1, api2)

    "return emailPreferencesSummaryView page for logged in user" in new Setup {
      val expectedCategoryMap = Map("CATEGORY_1" -> "Category 1")
      fetchSessionByIdReturns(sessionId, session)
      updateUserFlowSessionsReturnsSuccessfully(sessionId)

      val expectedAPIDisplayNames = Map(api1.serviceName -> api1.name, api2.serviceName -> api2.name)

      when(mockAPIService.fetchAllAPICategoryDetails()(*)).thenReturn(Future.successful(apiCategoryDetails))
      when(mockAPIService.fetchAPIDetails(eqTo(apis))(*)).thenReturn(Future.successful(fetchedAPis))

      val result = controllerUnderTest.emailPreferencesSummaryPage()(loggedInRequest)

      status(result) mustBe OK

      verify(mockEmailPreferencesSummaryView).apply(eqTo(EmailPreferencesSummaryViewData(expectedCategoryMap, expectedAPIDisplayNames)))(*, *, *, *)
    }

    "return emailPreferencesSummaryView page and set the view data correctly when the flash data value `unsubscribed` is true" in new Setup {
      fetchSessionByIdReturns(sessionId, sessionNoEMailPrefences)

      when(mockAPIService.fetchAllAPICategoryDetails()(*)).thenReturn(Future.successful(apiCategoryDetails))
      when(mockAPIService.fetchAPIDetails(eqTo(Set.empty))(*)).thenReturn(Future.successful(Seq.empty))

      val result = controllerUnderTest.emailPreferencesSummaryPage()(loggedInRequest.withFlash("unsubscribed" -> "true"))

      status(result) mustBe OK

      verify(mockEmailPreferencesSummaryView).apply(eqTo(EmailPreferencesSummaryViewData(Map.empty, Map.empty, unsubscribed = true)))(*, *, *, *)
    }

    "redirect to flow start page if user has no email preferences set (and has not just unsubscribed)" in new Setup {
      fetchSessionByIdReturns(sessionId, sessionNoEMailPrefences)

      val result = controllerUnderTest.emailPreferencesSummaryPage()(loggedInRequest)

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some(controllers.routes.EmailPreferences.flowStartPage().url)

      verifyZeroInteractions(mockAPIService)
      verifyZeroInteractions(mockEmailPreferencesSummaryView)
    }

    "redirect to login screen for non-logged in user" in new Setup {
      fetchSessionByIdReturnsNone(sessionId)

      val result = controllerUnderTest.emailPreferencesSummaryPage()(FakeRequest())

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some(controllers.routes.UserLoginAccount.login().url)

      verifyZeroInteractions(mockAPIService)
      verifyZeroInteractions(mockEmailPreferencesSummaryView)
    }
  }

  "emailPreferencesUnsubscribeAllPage" should {

    "return emailPreferencesUnsubcribeAllPage page for logged in user" in new Setup {
      fetchSessionByIdReturns(sessionId, session)


      val result = controllerUnderTest.unsubscribeAllPage()(loggedInRequest)
      status(result) mustBe OK

      verifyZeroInteractions(mockAPIService)
      verifyZeroInteractions(mockEmailPreferencesSummaryView)
      verify(mockEmailPreferencesUnsubscribeAllView).apply()(*, *, *, *)
    }


    "redirect to login screen for non-logged in user" in new Setup {
      fetchSessionByIdReturnsNone(sessionId)

      val result = controllerUnderTest.unsubscribeAllPage()(FakeRequest())

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some(controllers.routes.UserLoginAccount.login().url)

      verifyZeroInteractions(mockAPIService)
      verifyZeroInteractions(mockEmailPreferencesUnsubscribeAllView)
    }

  }

  "emailPreferencesUnsubscribeAllAction" should {

    "call unsubscribe all emailpreferences service and redirect to the summary page with session value set" in new Setup {
      fetchSessionByIdReturns(sessionId, session)

      when(mockEmailPreferencesService.removeEmailPreferences(*)(*)).thenReturn(Future.successful(true))
      val result = controllerUnderTest.unsubscribeAllAction()(loggedInRequest)
      status(result) mustBe SEE_OTHER

      redirectLocation(result) mustBe Some(controllers.routes.EmailPreferences.emailPreferencesSummaryPage().url)
      flash(result).get("unsubscribed") mustBe Some("true")
    }

    "redirect to login screen for non-logged in user" in new Setup {
      fetchSessionByIdReturnsNone(sessionId)

      val result = controllerUnderTest.unsubscribeAllAction()(FakeRequest())

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some(controllers.routes.UserLoginAccount.login().url)

      verifyZeroInteractions(mockAPIService)
      verifyZeroInteractions(mockEmailPreferencesService)
    }

  }

  "flowStartPage" should {
    "render the static start page" in new Setup {
      fetchSessionByIdReturns(sessionId, session)

      val result = controllerUnderTest.flowStartPage()(loggedInRequest)

      status(result) mustBe OK
      verify(mockEmailPreferencesStartView).apply()(*, *, *, *)
    }

    "redirect to login screen for non-logged in user" in new Setup {
      fetchSessionByIdReturnsNone(sessionId)

      val result = controllerUnderTest.flowStartPage()(FakeRequest())

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some(controllers.routes.UserLoginAccount.login().url)

      verifyZeroInteractions(mockEmailPreferencesStartView)
    }
  }

  "flowSelectCategoriesPage" should {
    val apiCategories = List(APICategoryDetails("api1", "API 1"))

    val apiDefinitions = Seq(ApiDefinition("api_1", "Api 1", "api - desc", ApiContext("context"), Seq("api1")))

    "render the page correctly when the user is logged in" in new Setup {
      fetchSessionByIdReturns(sessionId, session)
  
      when(mockAPIService.fetchAllAPICategoryDetails()(*)).thenReturn(Future.successful(apiCategories))
      when(mockEmailPreferencesService.fetchApiDefinitionsVisibleToUser(*)(*)).thenReturn(Future.successful(apiDefinitions))
      when(mockEmailPreferencesService.fetchFlowBySessionId(*)).thenReturn(Future.successful(EmailPreferencesFlow.fromDeveloperSession(loggedInDeveloper)))
      val result = controllerUnderTest.flowSelectCategoriesPage()(loggedInRequest)

      status(result) mustBe OK

      verify(mockEmailPreferencesSelectCategoriesView).apply(eqTo(apiCategories), eqTo(EmailPreferencesFlow.fromDeveloperSession(loggedInDeveloper)))(*, *, *, *)
    }

    "redirect to login screen for non-logged in user" in new Setup {
      fetchSessionByIdReturnsNone(sessionId)

      val result = controllerUnderTest.flowSelectCategoriesPage()(FakeRequest())

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some(controllers.routes.UserLoginAccount.login().url)

      verifyZeroInteractions(mockEmailPreferencesSelectCategoriesView)
    }

  }

  "flowSelectTopicsPage" should {

    "render the page correctly when the user is logged in" in new Setup {
      fetchSessionByIdReturns(sessionId, session)
      val expectedSelectedTopics = session.developer.emailPreferences.topics.map(_.value).toSet

      val result = controllerUnderTest.flowSelectTopicsPage()(loggedInRequest)

      status(result) mustBe OK

      verify(mockEmailPreferencesFlowSelectTopicView).apply(eqTo(expectedSelectedTopics))(*, *, *, *)
    }

    "redirect to login screen for non-logged in user" in new Setup {
      fetchSessionByIdReturnsNone(sessionId)

      val result = controllerUnderTest.flowSelectTopicsPage()(FakeRequest())

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some(controllers.routes.UserLoginAccount.login().url)

      verifyZeroInteractions(mockEmailPreferencesFlowSelectTopicView)
    }

  }
}
