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

import akka.stream.Materializer
import config.ApplicationConfig
import domain.models.apidefinitions.ApiContext
import domain.models.connectors.{ApiDefinition, ExtendedApiDefinition}
import domain.models.developers.{Developer, DeveloperSession, LoggedInState, Session}
import domain.models.emailpreferences
import domain.models.emailpreferences.{APICategoryDetails, TaxRegimeInterests}
import domain.models.flows.EmailPreferencesFlow
import mocks.service.{ErrorHandlerMock, SessionServiceMock}
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.i18n.MessagesApi
import play.api.libs.crypto.CookieSigner
import play.api.mvc.{AnyContentAsEmpty, AnyContentAsFormUrlEncoded, MessagesControllerComponents, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.filters.csrf.CSRF.TokenProvider
import service.EmailPreferencesService
import utils.WithLoggedInSession._
import views.emailpreferences.EmailPreferencesSummaryViewData
import views.html.emailpreferences._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class EmailPreferencesSpec extends PlaySpec with GuiceOneAppPerSuite with SessionServiceMock with ErrorHandlerMock {

  trait Setup {
    val mockEmailPreferencesService: EmailPreferencesService = mock[EmailPreferencesService]

    implicit val cookieSigner: CookieSigner = app.injector.instanceOf[CookieSigner]

    implicit lazy val materializer: Materializer = app.materializer

    lazy val messagesApi: MessagesApi = app.injector.instanceOf[MessagesApi]

    val mcc: MessagesControllerComponents = app.injector.instanceOf[MessagesControllerComponents]

    implicit val appConfig: ApplicationConfig = mock[ApplicationConfig]
    when(appConfig.nameOfPrincipalEnvironment).thenReturn("Production")
    when(appConfig.nameOfSubordinateEnvironment).thenReturn("Sandbox")

    val mockEmailPreferencesSummaryView: EmailPreferencesSummaryView = mock[EmailPreferencesSummaryView]
    val mockEmailPreferencesUnsubscribeAllView: EmailPreferencesUnsubscribeAllView = mock[EmailPreferencesUnsubscribeAllView]
    val mockEmailPreferencesStartView: FlowStartView = mock[FlowStartView]
    val mockEmailPreferencesSelectCategoriesView: FlowSelectCategoriesView = mock[FlowSelectCategoriesView]
    val mockEmailPreferencesFlowSelectTopicView: FlowSelectTopicsView = mock[FlowSelectTopicsView]
    val mockEmailPreferencesSelectApiView: FlowSelectApiView = mock[FlowSelectApiView]
    when(mockEmailPreferencesSummaryView.apply(*)(*, *, *, *)).thenReturn(play.twirl.api.HtmlFormat.empty)
    when(mockEmailPreferencesUnsubscribeAllView.apply()(*, *, *, *)).thenReturn(play.twirl.api.HtmlFormat.empty)
    when(mockEmailPreferencesStartView.apply()(*, *, *, *)).thenReturn(play.twirl.api.HtmlFormat.empty)
    when(mockEmailPreferencesSelectCategoriesView.apply(*, *, *)(*, *, *, *)).thenReturn(play.twirl.api.HtmlFormat.empty)
    when(mockEmailPreferencesFlowSelectTopicView.apply(*)(*, *, *, *)).thenReturn(play.twirl.api.HtmlFormat.empty)
    when(mockEmailPreferencesSelectApiView.apply(*, *, *, *)(*, *, *, *)).thenReturn(play.twirl.api.HtmlFormat.empty)

    val controllerUnderTest: EmailPreferences =
      new EmailPreferences(
        sessionServiceMock,
        mcc,
        mockErrorHandler,
        cookieSigner,
        mockEmailPreferencesService,
        mockEmailPreferencesSummaryView,
        mockEmailPreferencesUnsubscribeAllView,
        mockEmailPreferencesStartView,
        mockEmailPreferencesSelectCategoriesView,
        mockEmailPreferencesSelectApiView,
        mockEmailPreferencesFlowSelectTopicView
      )

    val emailPreferences: emailpreferences.EmailPreferences =
      domain.models.emailpreferences.EmailPreferences(List(TaxRegimeInterests("CATEGORY_1", Set("api1", "api2"))), Set.empty)
    val developer: Developer = Developer("third.party.developer@example.com", "John", "Doe")
    val developerWithEmailPrefences: Developer = developer.copy(emailPreferences = emailPreferences)
    val sessionId: String = "sessionId"
    val session: Session = Session(sessionId, developerWithEmailPrefences, LoggedInState.LOGGED_IN)
    val sessionNoEMailPrefences: Session = Session(sessionId, developer, LoggedInState.LOGGED_IN)
    val loggedInDeveloper: DeveloperSession = DeveloperSession(session)
    private val sessionParams: Seq[(String, String)] = Seq("csrfToken" -> app.injector.instanceOf[TokenProvider].generateToken)

    val loggedInRequest: FakeRequest[AnyContentAsEmpty.type] =
      FakeRequest().withLoggedIn(controllerUnderTest, implicitly)(sessionId).withSession(sessionParams: _*)
  }

  "emailPreferencesSummaryPage" should {
    val mockCategory1: APICategoryDetails = APICategoryDetails("CATEGORY_1", "Category 1")
    val mockCategory2: APICategoryDetails = APICategoryDetails("CATEGORY_2", "Category 2")
    val apiCategoryDetails: Seq[APICategoryDetails] = Seq(mockCategory1, mockCategory2)
    val api1: ExtendedApiDefinition = ExtendedApiDefinition("api1", "API 1", "desc", ApiContext("CATEGORY_1"))
    val api2: ExtendedApiDefinition = ExtendedApiDefinition("api2", "API 2", "desc2", ApiContext("CATEGORY_1"))
    val apis: Set[String] = Set(api1.serviceName, api2.serviceName)
    val fetchedAPis: Seq[ExtendedApiDefinition] = Seq(api1, api2)

    "return emailPreferencesSummaryView page for logged in user" in new Setup {
      val expectedCategoryMap: Map[String, String] = Map("CATEGORY_1" -> "Category 1")
      fetchSessionByIdReturns(sessionId, session)
      updateUserFlowSessionsReturnsSuccessfully(sessionId)

      val expectedAPIDisplayNames: Map[String, String] = Map(api1.serviceName -> api1.name, api2.serviceName -> api2.name)

      when(mockEmailPreferencesService.fetchAllAPICategoryDetails()(*)).thenReturn(Future.successful(apiCategoryDetails))
      when(mockEmailPreferencesService.fetchAPIDetails(eqTo(apis))(*)).thenReturn(Future.successful(fetchedAPis))

      val result: Future[Result] = controllerUnderTest.emailPreferencesSummaryPage()(loggedInRequest)

      status(result) mustBe OK

      verify(mockEmailPreferencesSummaryView).apply(eqTo(EmailPreferencesSummaryViewData(expectedCategoryMap, expectedAPIDisplayNames)))(*, *, *, *)
    }

    "return emailPreferencesSummaryView page and set the view data correctly when the flash data value `unsubscribed` is true" in new Setup {
      fetchSessionByIdReturns(sessionId, sessionNoEMailPrefences)

      when(mockEmailPreferencesService.fetchAllAPICategoryDetails()(*)).thenReturn(Future.successful(apiCategoryDetails))
      when(mockEmailPreferencesService.fetchAPIDetails(eqTo(Set.empty))(*)).thenReturn(Future.successful(Seq.empty))

      val result: Future[Result] = controllerUnderTest.emailPreferencesSummaryPage()(loggedInRequest.withFlash("unsubscribed" -> "true"))

      status(result) mustBe OK

      verify(mockEmailPreferencesSummaryView).apply(eqTo(EmailPreferencesSummaryViewData(Map.empty, Map.empty, unsubscribed = true)))(*, *, *, *)
    }

    "redirect to flow start page if user has no email preferences set (and has not just unsubscribed)" in new Setup {
      fetchSessionByIdReturns(sessionId, sessionNoEMailPrefences)

      val result: Future[Result] = controllerUnderTest.emailPreferencesSummaryPage()(loggedInRequest)

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some(controllers.routes.EmailPreferences.flowStartPage().url)

      verifyZeroInteractions(mockEmailPreferencesService)
      verifyZeroInteractions(mockEmailPreferencesSummaryView)
    }

    "redirect to login screen for non-logged in user" in new Setup {
      fetchSessionByIdReturnsNone(sessionId)

      val result: Future[Result] = controllerUnderTest.emailPreferencesSummaryPage()(FakeRequest())

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some(controllers.routes.UserLoginAccount.login().url)

      verifyZeroInteractions(mockEmailPreferencesService)
      verifyZeroInteractions(mockEmailPreferencesSummaryView)
    }
  }

  "emailPreferencesUnsubscribeAllPage" should {

    "return emailPreferencesUnsubcribeAllPage page for logged in user" in new Setup {
      fetchSessionByIdReturns(sessionId, session)


      val result: Future[Result] = controllerUnderTest.unsubscribeAllPage()(loggedInRequest)
      status(result) mustBe OK

      verifyZeroInteractions(mockEmailPreferencesService)
      verifyZeroInteractions(mockEmailPreferencesSummaryView)
      verify(mockEmailPreferencesUnsubscribeAllView).apply()(*, *, *, *)
    }


    "redirect to login screen for non-logged in user" in new Setup {
      fetchSessionByIdReturnsNone(sessionId)

      val result: Future[Result] = controllerUnderTest.unsubscribeAllPage()(FakeRequest())

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some(controllers.routes.UserLoginAccount.login().url)

      verifyZeroInteractions(mockEmailPreferencesService)
      verifyZeroInteractions(mockEmailPreferencesUnsubscribeAllView)
    }

  }

  "emailPreferencesUnsubscribeAllAction" should {

    "call unsubscribe all emailpreferences service and redirect to the summary page with session value set" in new Setup {
      fetchSessionByIdReturns(sessionId, session)

      when(mockEmailPreferencesService.removeEmailPreferences(*)(*)).thenReturn(Future.successful(true))
      val result: Future[Result] = controllerUnderTest.unsubscribeAllAction()(loggedInRequest)
      status(result) mustBe SEE_OTHER

      redirectLocation(result) mustBe Some(controllers.routes.EmailPreferences.emailPreferencesSummaryPage().url)
      flash(result).get("unsubscribed") mustBe Some("true")
    }

    "redirect to login screen for non-logged in user" in new Setup {
      fetchSessionByIdReturnsNone(sessionId)

      val result: Future[Result] = controllerUnderTest.unsubscribeAllAction()(FakeRequest())

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some(controllers.routes.UserLoginAccount.login().url)

      verifyZeroInteractions(mockEmailPreferencesService)
      verifyZeroInteractions(mockEmailPreferencesService)
    }

  }

  "flowStartPage" should {
    "render the static start page" in new Setup {
      fetchSessionByIdReturns(sessionId, session)

      val result: Future[Result] = controllerUnderTest.flowStartPage()(loggedInRequest)

      status(result) mustBe OK
      verify(mockEmailPreferencesStartView).apply()(*, *, *, *)
    }

    "redirect to login screen for non-logged in user" in new Setup {
      fetchSessionByIdReturnsNone(sessionId)

      val result: Future[Result] = controllerUnderTest.flowStartPage()(FakeRequest())

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some(controllers.routes.UserLoginAccount.login().url)

      verifyZeroInteractions(mockEmailPreferencesStartView)
    }
  }

  "flowSelectCategoriesPage" should {
    val apiCategories = List(APICategoryDetails("api1", "API 1"))

    "render the page correctly when the user is logged in" in new Setup {
      fetchSessionByIdReturns(sessionId, session)

      when(mockEmailPreferencesService.fetchCategoriesVisibleToUser(*)(*)).thenReturn(Future.successful(apiCategories))
      when(mockEmailPreferencesService.fetchFlow(*)).thenReturn(Future.successful(EmailPreferencesFlow.fromDeveloperSession(loggedInDeveloper)))
      val result: Future[Result] = controllerUnderTest.flowSelectCategoriesPage()(loggedInRequest)

      status(result) mustBe OK
      verify(mockEmailPreferencesSelectCategoriesView).apply(*, eqTo(apiCategories),
        eqTo(EmailPreferencesFlow.fromDeveloperSession(loggedInDeveloper).selectedCategories))(*, *, *, *)
    }

    "redirect to login screen for non-logged in user" in new Setup {
      fetchSessionByIdReturnsNone(sessionId)

      val result: Future[Result] = controllerUnderTest.flowSelectCategoriesPage()(FakeRequest())

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some(controllers.routes.UserLoginAccount.login().url)

      verifyZeroInteractions(mockEmailPreferencesSelectCategoriesView)
    }

  }

  "flowSelectCategoriesAction" should {
    val apiCategories = List(APICategoryDetails("api1", "API 1"))

    "handle form data and redirectToApisPage" in new Setup {
      val requestWithForm: FakeRequest[AnyContentAsFormUrlEncoded] = loggedInRequest
        .withFormUrlEncodedBody("taxRegime[0]" -> "a1", "taxRegime[1]" -> "a2", "taxRegime[2]" -> "a3")
      val categories = Set("a1", "a2", "a3")


      fetchSessionByIdReturns(sessionId, session)
      val flow: EmailPreferencesFlow = EmailPreferencesFlow.fromDeveloperSession(loggedInDeveloper).copy(selectedCategories = categories)

      when(mockEmailPreferencesService.updateCategories(eqTo(loggedInDeveloper), *)).thenReturn(Future.successful(flow))
      val result: Future[Result] = controllerUnderTest.flowSelectCategoriesAction()(requestWithForm)

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some(controllers.routes.EmailPreferences.flowSelectApisPage("a1").url)
    }

    "redirect back to self if form data is empty" in new Setup {
      fetchSessionByIdReturns(sessionId, session)

      when(mockEmailPreferencesService.fetchCategoriesVisibleToUser(*)(*)).thenReturn(Future.successful(apiCategories))
      when(mockEmailPreferencesService.fetchFlow(*)).thenReturn(Future.successful(EmailPreferencesFlow.fromDeveloperSession(loggedInDeveloper)))


      val result: Future[Result] = controllerUnderTest.flowSelectCategoriesAction()(loggedInRequest)

      status(result) mustBe BAD_REQUEST
      verify(mockEmailPreferencesSelectCategoriesView).apply(*, eqTo(apiCategories),
        eqTo(Set.empty[String]))(*, *, *, *)

    }

    "redirect to login screen for non-logged in user" in new Setup {
      fetchSessionByIdReturnsNone(sessionId)

      val result: Future[Result] = controllerUnderTest.flowSelectCategoriesAction()(FakeRequest())

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some(controllers.routes.UserLoginAccount.login().url)

      verifyZeroInteractions(mockEmailPreferencesSelectApiView)
    }
  }

  "flowSelectNoCategoriesAction" should {

    "update categories in flow object and redirect to topics page" in new Setup {
      fetchSessionByIdReturns(sessionId, session)

      when(mockEmailPreferencesService.updateCategories(eqTo(loggedInDeveloper), *))
        .thenReturn(Future.successful(EmailPreferencesFlow.fromDeveloperSession(loggedInDeveloper)))
      val result: Future[Result] = controllerUnderTest.flowSelectNoCategoriesAction()(loggedInRequest)

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some(controllers.routes.EmailPreferences.flowSelectTopicsPage().url)

    }


    "redirect to login screen for non-logged in user" in new Setup {
      fetchSessionByIdReturnsNone(sessionId)

      val result: Future[Result] = controllerUnderTest.flowSelectNoCategoriesAction()(FakeRequest())

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some(controllers.routes.UserLoginAccount.login().url)

      verifyZeroInteractions(mockEmailPreferencesFlowSelectTopicView)
    }
  }


  "flowSelectApisPage" should {
    val apiCategory = APICategoryDetails("category1", "Category 1")
    val visibleApis = List(ApiDefinition("serviceNameApi1", "nameApi1", "descriptionApi1", ApiContext("contextApi1"), Seq("category1", "category2")))

    // category passed to route
    // category is missing from route
    // when category details are not returned from email pref services?

    "render the page correctly when the user is logged in" in new Setup {
      fetchSessionByIdReturns(sessionId, session)
      updateUserFlowSessionsReturnsSuccessfully(sessionId)
      val emailFlow: EmailPreferencesFlow = EmailPreferencesFlow.fromDeveloperSession(loggedInDeveloper).copy(visibleApis = visibleApis)
      when(mockEmailPreferencesService.apiCategoryDetails(eqTo(apiCategory.category))(*)).thenReturn(Future.successful(Some(apiCategory)))
      when(mockEmailPreferencesService.fetchFlow(*)).thenReturn(Future.successful(emailFlow))

      val result: Future[Result] = controllerUnderTest.flowSelectApisPage(apiCategory.category)(loggedInRequest)

      status(result) mustBe OK
      verify(mockEmailPreferencesSelectApiView).apply(*,
        eqTo(apiCategory),
        eqTo(visibleApis),
        eqTo(Set.empty))(*, *, *, *)
    }

    "redirect to email summary page when category is missing from route" in new Setup {
      fetchSessionByIdReturns(sessionId, session)
      updateUserFlowSessionsReturnsSuccessfully(sessionId)

      val result: Future[Result] = controllerUnderTest.flowSelectApisPage("")(loggedInRequest)

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some(controllers.routes.EmailPreferences.emailPreferencesSummaryPage().url)
      verifyZeroInteractions(mockEmailPreferencesSelectApiView)
    }

    "redirect to login screen for non-logged in user" in new Setup {
      fetchSessionByIdReturnsNone(sessionId)

      val result: Future[Result] = controllerUnderTest.flowSelectApisPage("anyCategory")(FakeRequest())

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some(controllers.routes.UserLoginAccount.login().url)

      verifyZeroInteractions(mockEmailPreferencesSelectApiView)
    }

  }

  "flowSelectApiAction" should {
    val visibleApis = List(ApiDefinition("serviceNameApi1", "nameApi1", "descriptionApi1", ApiContext("contextApi1"), Seq("category1", "category2")))
    val apiCategory = APICategoryDetails("category1", "Category 1")
    val apiCategory2 = APICategoryDetails("category2", "Category 2")

    "redirect to the next category page" in new Setup {
      fetchSessionByIdReturns(sessionId, session)
      updateUserFlowSessionsReturnsSuccessfully(sessionId)

      val requestWithForm: FakeRequest[AnyContentAsFormUrlEncoded] = loggedInRequest
        .withFormUrlEncodedBody("currentCategory" -> "category1", "selectedApi[0]" -> "a1", "selectedApi[1]" -> "a2")

      val emailFlow: EmailPreferencesFlow = EmailPreferencesFlow.fromDeveloperSession(loggedInDeveloper)
        .copy(selectedCategories = Set(apiCategory.category, apiCategory2.category), visibleApis = visibleApis)
      when(mockEmailPreferencesService.fetchFlow(*)).thenReturn(Future.successful(emailFlow))
      when(mockEmailPreferencesService.apiCategoryDetails(*)(*)).thenReturn(Future.successful(Some(apiCategory)))
      when(mockEmailPreferencesService.updateSelectedApis(*, eqTo("category1"), eqTo(List("a1", "a2")))).thenReturn(Future.successful(emailFlow))

      val result: Future[Result] = controllerUnderTest.flowSelectApisAction()(requestWithForm)
      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some(controllers.routes.EmailPreferences.flowSelectApisPage(apiCategory2.category).url)
    }

    "redirect to the topics page when category last in alphabetical order" in new Setup {
      fetchSessionByIdReturns(sessionId, session)
      updateUserFlowSessionsReturnsSuccessfully(sessionId)

      val requestWithForm: FakeRequest[AnyContentAsFormUrlEncoded] = loggedInRequest
        .withFormUrlEncodedBody("currentCategory" -> "category2", "selectedApi[0]" -> "a1", "selectedApi[1]" -> "a2")

      val emailFlow: EmailPreferencesFlow = EmailPreferencesFlow.fromDeveloperSession(loggedInDeveloper)
        .copy(selectedCategories = Set(apiCategory.category, apiCategory2.category), visibleApis = visibleApis)
      when(mockEmailPreferencesService.fetchFlow(*)).thenReturn(Future.successful(emailFlow))
      when(mockEmailPreferencesService.apiCategoryDetails(*)(*)).thenReturn(Future.successful(Some(apiCategory)))
      when(mockEmailPreferencesService.updateSelectedApis(*, eqTo("category2"), eqTo(List("a1", "a2")))).thenReturn(Future.successful(emailFlow))

      val result: Future[Result] = controllerUnderTest.flowSelectApisAction()(requestWithForm)
      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some(controllers.routes.EmailPreferences.flowSelectTopicsPage().url)
    }


    "return 400 when form has missing elements" in new Setup {
      val requestWithForm: FakeRequest[AnyContentAsFormUrlEncoded] = loggedInRequest.withFormUrlEncodedBody("currentCategory" -> "category2")
      val emailFlow: EmailPreferencesFlow = EmailPreferencesFlow.fromDeveloperSession(loggedInDeveloper)
        .copy(selectedCategories = Set(apiCategory.category, apiCategory2.category), visibleApis = visibleApis)

      fetchSessionByIdReturns(sessionId, session)
      when(mockEmailPreferencesService.fetchFlow(*)).thenReturn(Future.successful(emailFlow))
      when(mockEmailPreferencesService.apiCategoryDetails(*)(*)).thenReturn(Future.successful(Some(apiCategory)))

      val result: Future[Result] = controllerUnderTest.flowSelectApisAction()(requestWithForm)

      status(result) mustBe BAD_REQUEST
      verify(mockEmailPreferencesService, times(0)).updateSelectedApis(*, *, *)
      verify(mockEmailPreferencesSelectApiView).apply(*,
        eqTo(apiCategory),
        eqTo(visibleApis),
        eqTo(Set.empty))(*, *, *, *)
    }

    "redirect to login screen for non-logged in user" in new Setup {
      fetchSessionByIdReturnsNone(sessionId)

      val result: Future[Result] = controllerUnderTest.flowSelectApisAction()(FakeRequest())

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some(controllers.routes.UserLoginAccount.login().url)

      verifyZeroInteractions(mockEmailPreferencesSelectApiView)
    }
  }

  "flowSelectTopicsPage" should {

    "render the page correctly when the user is logged in" in new Setup {
      fetchSessionByIdReturns(sessionId, session)

      val expectedSelectedTopics: Set[String] = session.developer.emailPreferences.topics.map(_.value)
      val emailFlow: EmailPreferencesFlow = EmailPreferencesFlow.fromDeveloperSession(loggedInDeveloper)
        .copy(selectedTopics = expectedSelectedTopics)
      when(mockEmailPreferencesService.fetchFlow(*)).thenReturn(Future.successful(emailFlow))

      val result: Future[Result] = controllerUnderTest.flowSelectTopicsPage()(loggedInRequest)

      status(result) mustBe OK

      verify(mockEmailPreferencesFlowSelectTopicView).apply(eqTo(expectedSelectedTopics))(*, *, *, *)
    }

    "redirect to login screen for non-logged in user" in new Setup {
      fetchSessionByIdReturnsNone(sessionId)

      val result: Future[Result] = controllerUnderTest.flowSelectTopicsPage()(FakeRequest())

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some(controllers.routes.UserLoginAccount.login().url)

      verifyZeroInteractions(mockEmailPreferencesFlowSelectTopicView)
    }

  }

  "flowSelectTopicsAction" should {

    "update email preferences then delete flow object when update is successful. Then redirect to summary page" in new Setup {
      fetchSessionByIdReturns(sessionId, session)

      val emailFlow: EmailPreferencesFlow = EmailPreferencesFlow.fromDeveloperSession(loggedInDeveloper)
      when(mockEmailPreferencesService.fetchFlow(*)).thenReturn(Future.successful(emailFlow))

      val requestWithForm = loggedInRequest.withFormUrlEncodedBody("topic[0]" -> "TECHNICAL")
      when(mockEmailPreferencesService.updateEmailPreferences(eqTo(developer.email), *)(*)).thenReturn(Future.successful(true))
      when(mockEmailPreferencesService.deleteFlow(eqTo(sessionId))).thenReturn(Future.successful(true))
      val result: Future[Result] = controllerUnderTest.flowSelectTopicsAction()(requestWithForm)


      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some(controllers.routes.EmailPreferences.emailPreferencesSummaryPage().url)

      verify(mockEmailPreferencesService).fetchFlow(eqTo(loggedInDeveloper))
      verify(mockEmailPreferencesService).updateEmailPreferences(eqTo(developer.email), eqTo(emailFlow.copy(selectedTopics = Set("TECHNICAL"))))(*)
      verify(mockEmailPreferencesService).deleteFlow(eqTo(sessionId))
    }

    "update email preferences then do not delete flow object when update fails. Then redirect to topics page" in new Setup {
      fetchSessionByIdReturns(sessionId, session)

      val emailFlow: EmailPreferencesFlow = EmailPreferencesFlow.fromDeveloperSession(loggedInDeveloper)
      when(mockEmailPreferencesService.fetchFlow(*)).thenReturn(Future.successful(emailFlow))

      val requestWithForm = loggedInRequest.withFormUrlEncodedBody("topic[0]" -> "TECHNICAL")
      when(mockEmailPreferencesService.updateEmailPreferences(eqTo(developer.email), *)(*)).thenReturn(Future.successful(false))

      val result: Future[Result] = controllerUnderTest.flowSelectTopicsAction()(requestWithForm)


      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some(controllers.routes.EmailPreferences.flowSelectTopicsPage().url)

      verify(mockEmailPreferencesService).fetchFlow(eqTo(loggedInDeveloper))
      verify(mockEmailPreferencesService).updateEmailPreferences(eqTo(developer.email), eqTo(emailFlow.copy(selectedTopics = Set("TECHNICAL"))))(*)
      verify(mockEmailPreferencesService, times(0)).deleteFlow(*)
    }

    "redirect back to topics page when form is empty" in new Setup {
      fetchSessionByIdReturns(sessionId, session)

      val result: Future[Result] = controllerUnderTest.flowSelectTopicsAction()(loggedInRequest)

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some(controllers.routes.EmailPreferences.flowSelectTopicsPage().url)
    }

    "redirect to login screen for non-logged in user" in new Setup {
      fetchSessionByIdReturnsNone(sessionId)

      val result: Future[Result] = controllerUnderTest.flowSelectTopicsAction()(FakeRequest())

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some(controllers.routes.UserLoginAccount.login().url)

      verifyZeroInteractions(mockEmailPreferencesService)
    }
  }
}
