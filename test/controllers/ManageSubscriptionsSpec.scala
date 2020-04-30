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

import java.util.UUID.randomUUID

import config.ErrorHandler
import domain.ApiSubscriptionFields.{SubscriptionFieldDefinition, SubscriptionFieldsWrapper, SubscriptionFieldValue}
import domain._
import org.joda.time.DateTimeZone
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import play.api.mvc.AnyContentAsEmpty
import org.mockito.Mockito.when
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.filters.csrf.CSRF.TokenProvider
import service.{ApplicationService, AuditService, SessionService, SubscriptionFieldsService}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.time.DateTimeUtils
import utils.WithCSRFAddToken
import utils.WithLoggedInSession._

import scala.concurrent.Future._
import domain.ApiSubscriptionFields._
import cats.data.NonEmptyList

import scala.concurrent.Future


class ManageSubscriptionsSpec extends BaseControllerSpec with WithCSRFAddToken {

  val failedNoApp = Future.failed(new ApplicationNotFound)

  val appId = "1234"
  val clientId = "clientId123"

  val developer = Developer("thirdpartydeveloper@example.com", "John", "Doe")
  val sessionId = "sessionId"
  val session = Session(sessionId, developer, LoggedInState.LOGGED_IN)

  val apiContext = "test"
  val apiVersion = "1.0"

  val loggedInUser = DeveloperSession(session)

  val partLoggedInSessionId = "partLoggedInSessionId"
  val partLoggedInSession =
    Session(partLoggedInSessionId, developer, LoggedInState.PART_LOGGED_IN_ENABLING_MFA)

  val application = Application(
    appId,
    clientId,
    "App name 1",
    DateTimeUtils.now,
    DateTimeUtils.now,
    Environment.PRODUCTION,
    Some("Description 1"),
    Set(Collaborator(loggedInUser.email, Role.ADMINISTRATOR)),
    state = ApplicationState.production(loggedInUser.email, ""),
    access = Standard(
      redirectUris = Seq("https://red1", "https://red2"),
      termsAndConditionsUrl = Some("http://tnc-url.com")
    )
  )

  val privilegedApplication = application.copy(id = "456", access = Privileged())

  val tokens =
    ApplicationToken("clientId", Seq(aClientSecret(), aClientSecret()), "token")

  private val sessionParams = Seq(
    "csrfToken" -> fakeApplication.injector.instanceOf[TokenProvider].generateToken
  )

  trait ManageSubscriptionsSetup {

    val mockSessionService = mock[SessionService]
    val mockAuditService = mock[AuditService]
    val mockApplicationService = mock[ApplicationService]
    val mockSubscriptionFieldsService = mock[SubscriptionFieldsService]
    val mockErrorHandler = fakeApplication.injector.instanceOf[ErrorHandler]

    val manageSubscriptionController = new ManageSubscriptions(
      mockSessionService,
      mockAuditService,
      mockApplicationService,
      mockErrorHandler,
      messagesApi,
      mockSubscriptionFieldsService,
      cookieSigner
    )

    when(mockSessionService.fetch(eqTo(sessionId))(any[HeaderCarrier]))
      .thenReturn(Some(session))

    when(mockApplicationService.fetchByApplicationId(eqTo(appId))(any[HeaderCarrier]))
      .thenReturn(successful(application))

    when(mockApplicationService.fetchByApplicationId(eqTo(privilegedApplication.id))(any[HeaderCarrier]))
      .thenReturn(successful(privilegedApplication))

    when(mockApplicationService.fetchByTeamMemberEmail(any())(any[HeaderCarrier]))
      .thenReturn(successful(List(application)))

    val loggedInRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
      .withLoggedIn(manageSubscriptionController, implicitly)(sessionId)
      .withSession(sessionParams: _*)

    val partLoggedInRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
      .withLoggedIn(manageSubscriptionController, implicitly)(partLoggedInSessionId)
      .withSession(sessionParams: _*)

    def generateName(prefix: String) = s"${prefix}-name"

    def generateField(prefix: String): SubscriptionFieldDefinition =
      SubscriptionFieldDefinition(
        name = generateName(prefix),
        description = s"${prefix}-description",
        shortDescription = s"${prefix}-short-description",
        hint = "",
        `type` = "STRING"
      )

    def generateValue(prefix: String) = s"${prefix}-value"

    def generateValueName(prefix: String, index: Int) = s"${prefix}-field-${index}"

    def generateFieldValue(prefix: String, index: Int): SubscriptionFieldValue =
      SubscriptionFieldValue(
        definition = generateField(prefix),
        value = generateValueName(prefix, index)
      )

    val WHO_CARES = "who cares"

    def generateWrapper(prefix: String, count: Int): Option[SubscriptionFieldsWrapper] = {
      val rawFields = (1 to count).map(i => generateFieldValue(prefix, i)).toList
      val nelFields = NonEmptyList.fromList(rawFields)

      nelFields.map(fs =>
        SubscriptionFieldsWrapper(
          applicationId = WHO_CARES,
          clientId = WHO_CARES,
          apiContext = WHO_CARES,
          apiVersion = WHO_CARES,
          fields = fs
        )
      )
    }

    def noConfigurationSubscription(prefix: String) =
      APISubscriptionStatus(
        name = generateName(prefix),
        serviceName = s"${prefix}-api",
        context = s"/${prefix}-api",
        apiVersion = APIVersion("1.0", APIStatus.STABLE),
        subscribed = true,
        requiresTrust = false,
        fields = None,
        isTestSupport = false
      )

    def configurationSubscription(prefix: String, count: Int) =
      noConfigurationSubscription(prefix).copy(fields = generateWrapper(prefix, count))
  }

  "ManageSubscriptions" when {
    "a user is logged in" should {
      "return the list subscription configuration page with no subscriptions and therefore no subscription field definitions" in new ManageSubscriptionsSetup {

        when(mockApplicationService.apisWithSubscriptions(eqTo(application))(any[HeaderCarrier]))
          .thenReturn(successful(List()))

        private val result =
          await(manageSubscriptionController.listApiSubscriptions(appId)(loggedInRequest))

        status(result) shouldBe NOT_FOUND
      }

      "return the list subscription configuration page with several subscriptions without subscription configuration" in new ManageSubscriptionsSetup {

        val subsData = Seq(noConfigurationSubscription("api1"), noConfigurationSubscription("api2"))

        when(mockApplicationService.apisWithSubscriptions(eqTo(application))(any[HeaderCarrier]))
          .thenReturn(successful(subsData))

        private val result =
          await(manageSubscriptionController.listApiSubscriptions(appId)(loggedInRequest))

        status(result) shouldBe NOT_FOUND
      }

      "return the list subscription configuration page with several subscriptions, some with subscription configuration" in new ManageSubscriptionsSetup {

        val subsData = Seq(
          configurationSubscription("api1", 3),
          configurationSubscription("api2", 1),
          noConfigurationSubscription("api3"),
          configurationSubscription("api4", 1).copy(subscribed = false)
        )

        when(mockApplicationService.apisWithSubscriptions(eqTo(application))(any[HeaderCarrier]))
          .thenReturn(successful(subsData))

        private val result =
          await(manageSubscriptionController.listApiSubscriptions(appId)(loggedInRequest))

        status(result) shouldBe OK
        bodyOf(result) should include(loggedInUser.displayedName)
        bodyOf(result) should include("Sign out")
        bodyOf(result) should include(
          "Edit the configuration for the APIs you have subscribed to."
        )

        bodyOf(result) should include(generateName("api1"))
        bodyOf(result) should include(generateValueName("api1", 1))
        bodyOf(result) should include(generateValueName("api1", 2))
        bodyOf(result) should include(generateValueName("api1", 3))
        bodyOf(result) should not include (generateValueName("api1", 4))

        bodyOf(result) should include(generateName("api2"))
        bodyOf(result) should include(generateValueName("api2", 1))

        bodyOf(result) should not include (generateName("api3"))
        bodyOf(result) should not include (generateName("api4"))
      }

      "return not found if app has no subscription field definitions" in new ManageSubscriptionsSetup {
        when(mockApplicationService.apisWithSubscriptions(eqTo(application))(any[HeaderCarrier]))
          .thenReturn(successful(Seq.empty))

        private val result =
          await(manageSubscriptionController.listApiSubscriptions(appId)(loggedInRequest))

        status(result) shouldBe NOT_FOUND
      }

      "return not found when trying to edit api subscription configuration for an api the application is not subscribed to" in new ManageSubscriptionsSetup {
        when(mockApplicationService.apisWithSubscriptions(eqTo(application))(any[HeaderCarrier]))
          .thenReturn(successful(Seq.empty))

        private val result = await(manageSubscriptionController.editApiMetadataPage(appId, apiContext, apiVersion)(loggedInRequest))

        status(result) shouldBe NOT_FOUND
      }

      "It renders the subscription configuration list page for a privileged application" in new ManageSubscriptionsSetup{
        val subsData = Seq(
          configurationSubscription("api1", 1)
        )

        when(mockApplicationService.apisWithSubscriptions(eqTo(privilegedApplication))(any[HeaderCarrier]))
          .thenReturn(successful(subsData))

        private val result =
          await(manageSubscriptionController.listApiSubscriptions(privilegedApplication.id)(loggedInRequest))

        status(result) shouldBe OK
      }
    }

    "when the user is not logged in" should {
      "return to the login page when the user attempts to list subscription configuration" in new ManageSubscriptionsSetup {

        val request = FakeRequest()

        private val result =
          await(manageSubscriptionController.listApiSubscriptions(appId)(request))

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some("/developer/login")
      }

      "return to the login page when the user attempts to edit subscription configuration" in new ManageSubscriptionsSetup {

        val request = FakeRequest()

        val fakeContext = "FAKE"
        val fakeVersion = "1.0"

        private val result =
          await(manageSubscriptionController.editApiMetadataPage(appId, fakeContext, fakeVersion)(request))

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some("/developer/login")
      }
    }
  }

  private def aClientSecret() =
    ClientSecret(
      randomUUID.toString,
      randomUUID.toString,
      DateTimeUtils.now.withZone(DateTimeZone.getDefault)
    )
}
