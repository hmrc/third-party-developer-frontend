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

package uk.gov.hmrc.thirdpartydeveloperfrontend.controllers

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import akka.stream.Materializer
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import views.emailpreferences.EmailPreferencesSummaryViewData
import views.html.emailpreferences._

import play.api.i18n.MessagesApi
import play.api.libs.crypto.CookieSigner
import play.api.mvc.{AnyContentAsEmpty, AnyContentAsFormUrlEncoded, MessagesControllerComponents, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.filters.csrf.CSRF.TokenProvider

import uk.gov.hmrc.apiplatform.modules.apis.domain.models.{ApiCategory, ApiDefinition, ServiceName}
import uk.gov.hmrc.apiplatform.modules.common.domain.models.{ApiContext, ApplicationId, UserId}
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.DeveloperBuilder
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ApplicationConfig
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.addapplication.routes.{AddApplication => AddApplicationRoutes}
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.profile.EmailPreferencesController
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.profile.routes.{EmailPreferencesController => EmailPreferencesControllerRoutes}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors.ApiType.REST_API
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors.CombinedApi
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.emailpreferences._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.flows.{EmailPreferencesFlowV2, FlowType, NewApplicationEmailPreferencesFlowV2}
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.service.{ErrorHandlerMock, SessionServiceMock}
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.EmailPreferencesService
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.LocalUserIdTracker
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithLoggedInSession._

class EmailPreferencesControllerSpec
    extends PlaySpec
    with GuiceOneAppPerSuite
    with SessionServiceMock
    with ErrorHandlerMock
    with DeveloperBuilder
    with LocalUserIdTracker {

  val category1 = ApiCategory.INCOME_TAX_MTD
  val category2 = ApiCategory.VAT

  trait Setup {
    val mockEmailPreferencesService: EmailPreferencesService = mock[EmailPreferencesService]

    implicit val cookieSigner: CookieSigner = app.injector.instanceOf[CookieSigner]

    implicit lazy val materializer: Materializer = app.materializer

    lazy val messagesApi: MessagesApi = app.injector.instanceOf[MessagesApi]

    val mcc: MessagesControllerComponents = app.injector.instanceOf[MessagesControllerComponents]

    implicit val appConfig: ApplicationConfig = mock[ApplicationConfig]
    when(appConfig.nameOfPrincipalEnvironment).thenReturn("Production")
    when(appConfig.nameOfSubordinateEnvironment).thenReturn("Sandbox")

    val mockEmailPreferencesSummaryView: EmailPreferencesSummaryView               = mock[EmailPreferencesSummaryView]
    val mockEmailPreferencesUnsubscribeAllView: EmailPreferencesUnsubscribeAllView = mock[EmailPreferencesUnsubscribeAllView]
    val mockEmailPreferencesStartView: FlowStartView                               = mock[FlowStartView]
    val mockEmailPreferencesSelectCategoriesView: FlowSelectCategoriesView         = mock[FlowSelectCategoriesView]
    val mockEmailPreferencesFlowSelectTopicView: FlowSelectTopicsView              = mock[FlowSelectTopicsView]
    val mockEmailPreferencesSelectApiView: FlowSelectApiView                       = mock[FlowSelectApiView]
    val mockSelectApisFromSubscriptionsView: SelectApisFromSubscriptionsView       = mock[SelectApisFromSubscriptionsView]
    val mockSelectTopicsFromSubscriptionsView: SelectTopicsFromSubscriptionsView   = mock[SelectTopicsFromSubscriptionsView]

    when(mockEmailPreferencesSummaryView.apply(*)(*, *, *, *)).thenReturn(play.twirl.api.HtmlFormat.empty)
    when(mockEmailPreferencesUnsubscribeAllView.apply()(*, *, *, *)).thenReturn(play.twirl.api.HtmlFormat.empty)
    when(mockEmailPreferencesStartView.apply()(*, *, *, *)).thenReturn(play.twirl.api.HtmlFormat.empty)
    when(mockEmailPreferencesSelectCategoriesView.apply(*, *, *)(*, *, *, *)).thenReturn(play.twirl.api.HtmlFormat.empty)
    when(mockEmailPreferencesFlowSelectTopicView.apply(*, *)(*, *, *, *)).thenReturn(play.twirl.api.HtmlFormat.empty)
    when(mockEmailPreferencesSelectApiView.apply(*, *, *, *)(*, *, *, *)).thenReturn(play.twirl.api.HtmlFormat.empty)
    when(mockSelectApisFromSubscriptionsView.apply(*, *, *[ApplicationId], *)(*, *, *, *)).thenReturn(play.twirl.api.HtmlFormat.empty)
    when(mockSelectTopicsFromSubscriptionsView.apply(*, *, *[ApplicationId])(*, *, *, *)).thenReturn(play.twirl.api.HtmlFormat.empty)

    val controllerUnderTest: EmailPreferencesController =
      new EmailPreferencesController(
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
        mockEmailPreferencesFlowSelectTopicView,
        mockSelectApisFromSubscriptionsView,
        mockSelectTopicsFromSubscriptionsView
      )

    val emailPreferences: EmailPreferences                   = EmailPreferences(List(TaxRegimeInterests(category1.toString, Set("api1", "api2"))), Set.empty)
    val developer: Developer                                 = buildDeveloper()
    val developerWithEmailPrefences: Developer               = developer.copy(emailPreferences = emailPreferences)
    val sessionId: String                                    = "sessionId"
    val session: Session                                     = Session(sessionId, developerWithEmailPrefences, LoggedInState.LOGGED_IN)
    val sessionNoEMailPrefences: Session                     = Session(sessionId, developer, LoggedInState.LOGGED_IN)
    val loggedInDeveloper: DeveloperSession                  = DeveloperSession(session)
    private val sessionParams: Seq[(String, String)]         = Seq("csrfToken" -> app.injector.instanceOf[TokenProvider].generateToken)
    val loggedInRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest().withLoggedIn(controllerUnderTest, implicitly)(sessionId).withSession(sessionParams: _*)
  }

  "emailPreferencesSummaryPage" should {
    val mockCategory1: APICategoryDisplayDetails            = APICategoryDisplayDetails.from(category1)
    val mockCategory2: APICategoryDisplayDetails            = APICategoryDisplayDetails.from(category2)
    val apiCategoryDetails: List[APICategoryDisplayDetails] = List(mockCategory1, mockCategory2)
    val api1: ApiDefinition                                 =
      ApiDefinition(ServiceName("api1"), "http://serviceBaseUrl", "API 1", "desc", ApiContext("CATEGORY_1"), List.empty, false, false, None, List(ApiCategory.INCOME_TAX_MTD))
    val api2: ApiDefinition                                 =
      ApiDefinition(ServiceName("api2"), "http://serviceBaseUrl", "API 2", "desc2", ApiContext("CATEGORY_1"), List.empty, false, false, None, List(ApiCategory.VAT))
    val apis: Set[ServiceName]                              = Set(api1.serviceName, api2.serviceName)

    val extendedApiOne: CombinedApi    = CombinedApi(ServiceName("api1"), "API 1", List(ApiCategory.INCOME_TAX_MTD), REST_API)
    val extendedApiTwo: CombinedApi    = CombinedApi(ServiceName("api2"), "API 2", List(ApiCategory.VAT), REST_API)
    val fetchedAPis: List[CombinedApi] = List(extendedApiOne, extendedApiTwo)

    "return emailPreferencesSummaryView page for logged in user" in new Setup {
      val expectedCategoryMap: Map[String, String] = Map(category1.toString() -> category1.displayText)
      fetchSessionByIdReturns(sessionId, session)
      updateUserFlowSessionsReturnsSuccessfully(sessionId)

      // val expectedAPIDisplayNames: Map[String, String] = Map(api1.serviceName -> api1.name, api2.serviceName -> api2.name)
      val expectedAPIDisplayNames: Map[String, String] =
        Map(extendedApiOne.serviceName.value -> extendedApiOne.displayName, extendedApiTwo.serviceName.value -> extendedApiTwo.displayName)

      when(mockEmailPreferencesService.fetchAllAPICategoryDetails()(*)).thenReturn(Future.successful(apiCategoryDetails))
      when(mockEmailPreferencesService.fetchAPIDetails(eqTo(apis))(*)).thenReturn(Future.successful(fetchedAPis))

      val result: Future[Result] = controllerUnderTest.emailPreferencesSummaryPage()(loggedInRequest)

      status(result) mustBe OK

      verify(mockEmailPreferencesSummaryView).apply(eqTo(EmailPreferencesSummaryViewData(expectedCategoryMap, expectedAPIDisplayNames)))(*, *, *, *)
    }

    "return emailPreferencesSummaryView page and set the view data correctly when the flash data value `unsubscribed` is true" in new Setup {
      fetchSessionByIdReturns(sessionId, sessionNoEMailPrefences)

      when(mockEmailPreferencesService.fetchAllAPICategoryDetails()(*)).thenReturn(Future.successful(apiCategoryDetails))
      when(mockEmailPreferencesService.fetchAPIDetails(eqTo(Set.empty))(*)).thenReturn(Future.successful(List.empty))

      val result: Future[Result] = controllerUnderTest.emailPreferencesSummaryPage()(loggedInRequest.withFlash("unsubscribed" -> "true"))

      status(result) mustBe OK

      verify(mockEmailPreferencesSummaryView).apply(eqTo(EmailPreferencesSummaryViewData(Map.empty, Map.empty, unsubscribed = true)))(*, *, *, *)
    }

    "redirect to login screen for non-logged in user" in new Setup {
      fetchSessionByIdReturnsNone(sessionId)

      val result: Future[Result] = controllerUnderTest.emailPreferencesSummaryPage()(FakeRequest())

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some(routes.UserLoginAccount.login.url)

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
      redirectLocation(result) mustBe Some(routes.UserLoginAccount.login.url)

      verifyZeroInteractions(mockEmailPreferencesService)
      verifyZeroInteractions(mockEmailPreferencesUnsubscribeAllView)
    }

  }

  "emailPreferencesUnsubscribeAllAction" should {

    "call unsubscribe all emailpreferences service and redirect to the summary page with session value set" in new Setup {
      fetchSessionByIdReturns(sessionId, session)

      when(mockEmailPreferencesService.removeEmailPreferences(*[UserId])(*)).thenReturn(Future.successful(true))
      val result: Future[Result] = controllerUnderTest.unsubscribeAllAction()(loggedInRequest)
      status(result) mustBe SEE_OTHER

      redirectLocation(result) mustBe Some(EmailPreferencesControllerRoutes.emailPreferencesSummaryPage().url)
      flash(result).get("unsubscribed") mustBe Some("true")
    }

    "redirect to login screen for non-logged in user" in new Setup {
      fetchSessionByIdReturnsNone(sessionId)

      val result: Future[Result] = controllerUnderTest.unsubscribeAllAction()(FakeRequest())

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some(routes.UserLoginAccount.login.url)

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
      redirectLocation(result) mustBe Some(routes.UserLoginAccount.login.url)

      verifyZeroInteractions(mockEmailPreferencesStartView)
    }
  }

  "flowSelectCategoriesPage" should {
    val apiCategories = List(APICategoryDisplayDetails("api1", "API 1"))

    "render the page correctly when the user is logged in" in new Setup {
      fetchSessionByIdReturns(sessionId, session)

      when(mockEmailPreferencesService.fetchCategoriesVisibleToUser(*, *)(*)).thenReturn(Future.successful(apiCategories))
      when(mockEmailPreferencesService.fetchEmailPreferencesFlow(*)).thenReturn(Future.successful(EmailPreferencesFlowV2.fromDeveloperSession(loggedInDeveloper)))
      val result: Future[Result] = controllerUnderTest.flowSelectCategoriesPage()(loggedInRequest)

      status(result) mustBe OK
      verify(mockEmailPreferencesSelectCategoriesView).apply(*, eqTo(apiCategories), eqTo(EmailPreferencesFlowV2.fromDeveloperSession(loggedInDeveloper).selectedCategories))(
        *,
        *,
        *,
        *
      )
    }

    "redirect to login screen for non-logged in user" in new Setup {
      fetchSessionByIdReturnsNone(sessionId)

      val result: Future[Result] = controllerUnderTest.flowSelectCategoriesPage()(FakeRequest())

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some(routes.UserLoginAccount.login.url)

      verifyZeroInteractions(mockEmailPreferencesSelectCategoriesView)
    }

  }

  "flowSelectCategoriesAction" should {
    val apiCategories = List(APICategoryDisplayDetails("api1", "API 1"))

    "handle form data and redirectToApisPage" in new Setup {
      val requestWithForm: FakeRequest[AnyContentAsFormUrlEncoded] = loggedInRequest
        .withFormUrlEncodedBody("taxRegime[0]" -> "a1", "taxRegime[1]" -> "a2", "taxRegime[2]" -> "a3")
      val categories                                               = Set("a1", "a2", "a3")

      fetchSessionByIdReturns(sessionId, session)
      val flow: EmailPreferencesFlowV2 = EmailPreferencesFlowV2.fromDeveloperSession(loggedInDeveloper).copy(selectedCategories = categories)

      when(mockEmailPreferencesService.updateCategories(eqTo(loggedInDeveloper), *)).thenReturn(Future.successful(flow))
      val result: Future[Result] = controllerUnderTest.flowSelectCategoriesAction()(requestWithForm)

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some(EmailPreferencesControllerRoutes.flowSelectApisPage("a1").url)
    }

    "redirect back to self if form data is empty" in new Setup {
      fetchSessionByIdReturns(sessionId, session)

      when(mockEmailPreferencesService.fetchCategoriesVisibleToUser(*, *)(*)).thenReturn(Future.successful(apiCategories))
      when(mockEmailPreferencesService.fetchEmailPreferencesFlow(*)).thenReturn(Future.successful(EmailPreferencesFlowV2.fromDeveloperSession(loggedInDeveloper)))

      val result: Future[Result] = controllerUnderTest.flowSelectCategoriesAction()(loggedInRequest)

      status(result) mustBe BAD_REQUEST
      verify(mockEmailPreferencesSelectCategoriesView).apply(*, eqTo(apiCategories), eqTo(Set.empty[String]))(*, *, *, *)

    }

    "redirect to login screen for non-logged in user" in new Setup {
      fetchSessionByIdReturnsNone(sessionId)

      val result: Future[Result] = controllerUnderTest.flowSelectCategoriesAction()(FakeRequest())

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some(routes.UserLoginAccount.login.url)

      verifyZeroInteractions(mockEmailPreferencesSelectApiView)
    }
  }

  "flowSelectNoCategoriesAction" should {

    "update categories in flow object and redirect to topics page" in new Setup {
      fetchSessionByIdReturns(sessionId, session)

      when(mockEmailPreferencesService.updateCategories(eqTo(loggedInDeveloper), *))
        .thenReturn(Future.successful(EmailPreferencesFlowV2.fromDeveloperSession(loggedInDeveloper)))
      val result: Future[Result] = controllerUnderTest.flowSelectNoCategoriesAction()(loggedInRequest)

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some(EmailPreferencesControllerRoutes.flowSelectTopicsPage().url)

    }

    "redirect to login screen for non-logged in user" in new Setup {
      fetchSessionByIdReturnsNone(sessionId)

      val result: Future[Result] = controllerUnderTest.flowSelectNoCategoriesAction()(FakeRequest())

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some(routes.UserLoginAccount.login.url)

      verifyZeroInteractions(mockEmailPreferencesFlowSelectTopicView)
    }
  }

  "flowSelectApisPage" should {
    val apiCategory = APICategoryDisplayDetails("OTHER", "Other")
    val visibleApis = List(CombinedApi(ServiceName("nameApi1"), "serviceNameApi1", List(ApiCategory.EXAMPLE, ApiCategory.OTHER), REST_API))

    // category passed to route
    // category is missing from route
    // when category details are not returned from email pref services?

    "render the page correctly when the user is logged in" in new Setup {
      fetchSessionByIdReturns(sessionId, session)
      updateUserFlowSessionsReturnsSuccessfully(sessionId)
      val emailFlow: EmailPreferencesFlowV2 = EmailPreferencesFlowV2.fromDeveloperSession(loggedInDeveloper).copy(visibleApis = visibleApis)
      when(mockEmailPreferencesService.apiCategoryDetails(eqTo(apiCategory.category))(*)).thenReturn(Future.successful(Some(apiCategory)))
      when(mockEmailPreferencesService.fetchEmailPreferencesFlow(*)).thenReturn(Future.successful(emailFlow))

      val result: Future[Result] = controllerUnderTest.flowSelectApisPage(apiCategory.category)(loggedInRequest)

      status(result) mustBe OK
      verify(mockEmailPreferencesSelectApiView).apply(*, eqTo(apiCategory), eqTo(visibleApis), eqTo(Set.empty))(*, *, *, *)
    }

    "redirect to email summary page when category is missing from route" in new Setup {
      fetchSessionByIdReturns(sessionId, session)
      updateUserFlowSessionsReturnsSuccessfully(sessionId)

      val result: Future[Result] = controllerUnderTest.flowSelectApisPage("")(loggedInRequest)

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some(EmailPreferencesControllerRoutes.emailPreferencesSummaryPage().url)
      verifyZeroInteractions(mockEmailPreferencesSelectApiView)
    }

    "redirect to login screen for non-logged in user" in new Setup {
      fetchSessionByIdReturnsNone(sessionId)

      val result: Future[Result] = controllerUnderTest.flowSelectApisPage("anyCategory")(FakeRequest())

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some(routes.UserLoginAccount.login.url)

      verifyZeroInteractions(mockEmailPreferencesSelectApiView)
    }

  }

  "flowSelectApiAction" should {

    val visibleApis  = List(CombinedApi(ServiceName("nameApi1"), "serviceNameApi1", List(category2, category1), REST_API))
    val apiCategory1 = APICategoryDisplayDetails(category1.toString(), category1.displayText)
    val apiCategory2 = APICategoryDisplayDetails(category2.toString(), category2.displayText)

    "redirect to the next category page" in new Setup {
      fetchSessionByIdReturns(sessionId, session)
      updateUserFlowSessionsReturnsSuccessfully(sessionId)

      val requestWithForm: FakeRequest[AnyContentAsFormUrlEncoded] = loggedInRequest
        .withFormUrlEncodedBody("currentCategory" -> category1.toString(), "selectedApi[0]" -> "a1", "selectedApi[1]" -> "a2", "apiRadio" -> "SOME_APIS")

      val emailFlow: EmailPreferencesFlowV2 = EmailPreferencesFlowV2.fromDeveloperSession(loggedInDeveloper)
        .copy(selectedCategories = Set(category1.toString(), category2.toString()), visibleApis = visibleApis)
      when(mockEmailPreferencesService.fetchEmailPreferencesFlow(*)).thenReturn(Future.successful(emailFlow))
      when(mockEmailPreferencesService.apiCategoryDetails(*)(*)).thenReturn(Future.successful(Some(apiCategory1)))
      when(mockEmailPreferencesService.updateSelectedApis(*, eqTo(category1.toString()), eqTo(List("a1", "a2")))).thenReturn(Future.successful(emailFlow))

      val result: Future[Result] = controllerUnderTest.flowSelectApisAction()(requestWithForm)
      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some(EmailPreferencesControllerRoutes.flowSelectApisPage(apiCategory2.category).url)
    }

    "redirect to the topics page when category last in alphabetical order" in new Setup {
      fetchSessionByIdReturns(sessionId, session)
      updateUserFlowSessionsReturnsSuccessfully(sessionId)

      val requestWithForm: FakeRequest[AnyContentAsFormUrlEncoded] = loggedInRequest
        .withFormUrlEncodedBody("currentCategory" -> category2.toString(), "selectedApi[0]" -> "a1", "selectedApi[1]" -> "a2", "apiRadio" -> "SOME_APIS")

      val emailFlow: EmailPreferencesFlowV2 = EmailPreferencesFlowV2.fromDeveloperSession(loggedInDeveloper)
        .copy(selectedCategories = Set(category1.toString(), category2.toString()), visibleApis = visibleApis)
      when(mockEmailPreferencesService.fetchEmailPreferencesFlow(*)).thenReturn(Future.successful(emailFlow))
      when(mockEmailPreferencesService.apiCategoryDetails(*)(*)).thenReturn(Future.successful(Some(apiCategory1)))
      when(mockEmailPreferencesService.updateSelectedApis(*, eqTo(category2.toString()), eqTo(List("a1", "a2")))).thenReturn(Future.successful(emailFlow))

      val result: Future[Result] = controllerUnderTest.flowSelectApisAction()(requestWithForm)
      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some(EmailPreferencesControllerRoutes.flowSelectTopicsPage().url)
    }

    "return 400 when form has missing elements" in new Setup {
      val requestWithForm: FakeRequest[AnyContentAsFormUrlEncoded] = loggedInRequest.withFormUrlEncodedBody("currentCategory" -> category2.toString())
      val emailFlow: EmailPreferencesFlowV2                        = EmailPreferencesFlowV2.fromDeveloperSession(loggedInDeveloper)
        .copy(selectedCategories = Set(apiCategory1.category, apiCategory2.category), visibleApis = visibleApis)

      fetchSessionByIdReturns(sessionId, session)
      when(mockEmailPreferencesService.fetchEmailPreferencesFlow(*)).thenReturn(Future.successful(emailFlow))
      when(mockEmailPreferencesService.apiCategoryDetails(*)(*)).thenReturn(Future.successful(Some(apiCategory1)))

      val result: Future[Result] = controllerUnderTest.flowSelectApisAction()(requestWithForm)

      status(result) mustBe BAD_REQUEST
      verify(mockEmailPreferencesService, times(0)).updateSelectedApis(*, *, *)
      verify(mockEmailPreferencesSelectApiView).apply(*, eqTo(apiCategory1), eqTo(visibleApis), eqTo(Set.empty))(*, *, *, *)
    }

    "redirect to login screen for non-logged in user" in new Setup {
      fetchSessionByIdReturnsNone(sessionId)

      val result: Future[Result] = controllerUnderTest.flowSelectApisAction()(FakeRequest())

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some(routes.UserLoginAccount.login.url)

      verifyZeroInteractions(mockEmailPreferencesSelectApiView)
    }
  }

  "flowSelectTopicsPage" should {

    "render the page correctly when the user is logged in" in new Setup {
      fetchSessionByIdReturns(sessionId, session)

      val expectedSelectedTopics: Set[String] = session.developer.emailPreferences.topics.map(_.value)
      val emailFlow: EmailPreferencesFlowV2   = EmailPreferencesFlowV2.fromDeveloperSession(loggedInDeveloper)
        .copy(selectedTopics = expectedSelectedTopics)
      when(mockEmailPreferencesService.fetchEmailPreferencesFlow(*)).thenReturn(Future.successful(emailFlow))

      val result: Future[Result] = controllerUnderTest.flowSelectTopicsPage()(loggedInRequest)

      status(result) mustBe OK

      verify(mockEmailPreferencesFlowSelectTopicView).apply(*, eqTo(expectedSelectedTopics))(*, *, *, *)
    }

    "redirect to login screen for non-logged in user" in new Setup {
      fetchSessionByIdReturnsNone(sessionId)

      val result: Future[Result] = controllerUnderTest.flowSelectTopicsPage()(FakeRequest())

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some(routes.UserLoginAccount.login.url)

      verifyZeroInteractions(mockEmailPreferencesFlowSelectTopicView)
    }

  }

  "flowSelectTopicsAction" should {

    "update email preferences then delete flow object when update is successful. Then redirect to summary page" in new Setup {
      fetchSessionByIdReturns(sessionId, session)

      val emailFlow: EmailPreferencesFlowV2 = EmailPreferencesFlowV2.fromDeveloperSession(loggedInDeveloper)
      when(mockEmailPreferencesService.fetchEmailPreferencesFlow(*)).thenReturn(Future.successful(emailFlow))

      val requestWithForm        = loggedInRequest.withFormUrlEncodedBody("topic[0]" -> "TECHNICAL")
      when(mockEmailPreferencesService.updateEmailPreferences(eqTo(developer.userId), *)(*)).thenReturn(Future.successful(true))
      when(mockEmailPreferencesService.deleteFlow(eqTo(sessionId), eqTo(FlowType.EMAIL_PREFERENCES_V2))).thenReturn(Future.successful(true))
      val result: Future[Result] = controllerUnderTest.flowSelectTopicsAction()(requestWithForm)

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some(EmailPreferencesControllerRoutes.emailPreferencesSummaryPage().url)

      verify(mockEmailPreferencesService).fetchEmailPreferencesFlow(eqTo(loggedInDeveloper))
      verify(mockEmailPreferencesService).updateEmailPreferences(eqTo(developer.userId), eqTo(emailFlow.copy(selectedTopics = Set("TECHNICAL"))))(*)
      verify(mockEmailPreferencesService).deleteFlow(eqTo(sessionId), eqTo(FlowType.EMAIL_PREFERENCES_V2))
    }

    "update email preferences then do not delete flow object when update fails. Then redirect to topics page" in new Setup {
      fetchSessionByIdReturns(sessionId, session)

      val emailFlow: EmailPreferencesFlowV2 = EmailPreferencesFlowV2.fromDeveloperSession(loggedInDeveloper)
      when(mockEmailPreferencesService.fetchEmailPreferencesFlow(*)).thenReturn(Future.successful(emailFlow))

      val requestWithForm = loggedInRequest.withFormUrlEncodedBody("topic[0]" -> "TECHNICAL")
      when(mockEmailPreferencesService.updateEmailPreferences(eqTo(developer.userId), *)(*)).thenReturn(Future.successful(false))

      val result: Future[Result] = controllerUnderTest.flowSelectTopicsAction()(requestWithForm)

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some(EmailPreferencesControllerRoutes.flowSelectTopicsPage().url)

      verify(mockEmailPreferencesService).fetchEmailPreferencesFlow(eqTo(loggedInDeveloper))
      verify(mockEmailPreferencesService).updateEmailPreferences(eqTo(developer.userId), eqTo(emailFlow.copy(selectedTopics = Set("TECHNICAL"))))(*)
      verify(mockEmailPreferencesService, times(0)).deleteFlow(*, eqTo(FlowType.EMAIL_PREFERENCES_V2))
    }

    "return 400 and re-display topics page when form is empty" in new Setup {
      fetchSessionByIdReturns(sessionId, session)

      val result: Future[Result] = controllerUnderTest.flowSelectTopicsAction()(loggedInRequest)

      status(result) mustBe BAD_REQUEST
      verify(mockEmailPreferencesFlowSelectTopicView).apply(*, eqTo(Set.empty))(*, *, *, *)
    }

    "redirect to login screen for non-logged in user" in new Setup {
      fetchSessionByIdReturnsNone(sessionId)

      val result: Future[Result] = controllerUnderTest.flowSelectTopicsAction()(FakeRequest())

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some(routes.UserLoginAccount.login.url)

      verifyZeroInteractions(mockEmailPreferencesService)
    }
  }

  "selectApisFromSubscriptionsPage" should {
    val applicationId = ApplicationId.random

    "render the page correctly" in new Setup {
      val newApplicationEmailPreferencesFlow = NewApplicationEmailPreferencesFlowV2(
        loggedInDeveloper.session.sessionId,
        loggedInDeveloper.developer.emailPreferences,
        applicationId,
        Set.empty,
        Set.empty,
        Set.empty
      )

      fetchSessionByIdReturns(sessionId, session)

      when(mockEmailPreferencesService.fetchNewApplicationEmailPreferencesFlow(*, *[ApplicationId])).thenReturn(Future.successful(newApplicationEmailPreferencesFlow))
      when(mockEmailPreferencesService.updateMissingSubscriptions(*, *[ApplicationId], *)).thenReturn(Future.successful(newApplicationEmailPreferencesFlow))

      val result: Future[Result] = controllerUnderTest.selectApisFromSubscriptionsPage(applicationId)(loggedInRequest)

      status(result) mustBe OK
      verify(mockSelectApisFromSubscriptionsView).apply(
        *,
        *,
        eqTo(applicationId),
        eqTo(Set.empty)
      )(*, *, *, *)
    }
  }

  "selectApisFromSubscriptionsAction" should {
    val applicationId: ApplicationId = ApplicationId.random

    "redirect to the topics page" in new Setup {
      fetchSessionByIdReturns(sessionId, session)
      updateUserFlowSessionsReturnsSuccessfully(sessionId)

      val requestWithForm: FakeRequest[AnyContentAsFormUrlEncoded] = loggedInRequest
        .withFormUrlEncodedBody("selectedApi[0]" -> "a1", "selectedApi[1]" -> "a2", "applicationId" -> applicationId.toString())

      when(mockEmailPreferencesService.updateNewApplicationSelectedApis(*, *[ApplicationId], *)(*)).thenReturn(Future.successful(mock[NewApplicationEmailPreferencesFlowV2]))

      val result: Future[Result] = controllerUnderTest.selectApisFromSubscriptionsAction(applicationId)(requestWithForm)
      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some(EmailPreferencesControllerRoutes.selectTopicsFromSubscriptionsPage(applicationId).url)

      verify(mockEmailPreferencesService).updateNewApplicationSelectedApis(eqTo(loggedInDeveloper), eqTo(applicationId), eqTo(Set(ServiceName("a1"), ServiceName("a2"))))(*)
    }
  }

  "selectTopicsFromSubscriptionsPage" should {
    val applicationId = ApplicationId.random

    "render the page correctly" in new Setup {
      val newApplicationEmailPreferencesFlow = NewApplicationEmailPreferencesFlowV2(
        loggedInDeveloper.session.sessionId,
        loggedInDeveloper.developer.emailPreferences,
        applicationId,
        Set.empty,
        Set.empty,
        Set.empty
      )

      fetchSessionByIdReturns(sessionId, session)

      when(mockEmailPreferencesService.fetchNewApplicationEmailPreferencesFlow(*, *[ApplicationId])).thenReturn(Future.successful(newApplicationEmailPreferencesFlow))
      when(mockEmailPreferencesService.updateEmailPreferences(eqTo(developer.userId), *)(*)).thenReturn(Future.successful(true))

      val result: Future[Result] = controllerUnderTest.selectTopicsFromSubscriptionsPage(applicationId)(loggedInRequest)

      status(result) mustBe OK
      verify(mockSelectTopicsFromSubscriptionsView).apply(
        *,
        eqTo(Set.empty),
        eqTo(applicationId)
      )(*, *, *, *)
    }
  }

  "selectTopicsFromSubscriptionsAction" should {
    val applicationId: ApplicationId = ApplicationId.random

    "redirect to the add application success page" in new Setup {
      val newApplicationEmailPreferencesFlow = NewApplicationEmailPreferencesFlowV2(
        loggedInDeveloper.session.sessionId,
        loggedInDeveloper.developer.emailPreferences,
        applicationId,
        Set.empty,
        Set.empty,
        Set.empty
      )

      fetchSessionByIdReturns(sessionId, session)
      updateUserFlowSessionsReturnsSuccessfully(sessionId)

      val requestWithForm: FakeRequest[AnyContentAsFormUrlEncoded] = loggedInRequest
        .withFormUrlEncodedBody("topic[0]" -> "a1", "applicationId" -> applicationId.toString())

      when(mockEmailPreferencesService.fetchNewApplicationEmailPreferencesFlow(*, *[ApplicationId])).thenReturn(Future.successful(newApplicationEmailPreferencesFlow))
      when(mockEmailPreferencesService.updateEmailPreferences(*[UserId], *)(*)).thenReturn(Future.successful(true))
      when(mockEmailPreferencesService.deleteFlow(*, *)).thenReturn(Future.successful(true))

      val result: Future[Result] = controllerUnderTest.selectTopicsFromSubscriptionsAction(applicationId)(requestWithForm)
      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some(AddApplicationRoutes.addApplicationSuccess(applicationId).url)

      verify(mockEmailPreferencesService).updateEmailPreferences(eqTo(developer.userId), eqTo(newApplicationEmailPreferencesFlow.copy(selectedTopics = Set("a1"))))(*)
    }
  }
}
