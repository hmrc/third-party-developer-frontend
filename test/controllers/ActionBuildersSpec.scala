/*
 * Copyright 2021 HM Revenue & Customs
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

import config.{ApplicationConfig, ErrorHandler}
import domain.models.apidefinitions.{ApiContext, ApiVersion}
import domain.models.developers.DeveloperSession
import helpers.LoggedInRequestTestHelper
import mocks.service._
import play.api.http.Status.{NOT_FOUND, OK}
import play.api.libs.crypto.CookieSigner
import play.api.mvc.{AnyContent, MessagesControllerComponents}
import play.api.test.Helpers._
import service.{ApplicationActionService, ApplicationService, SessionService}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import utils.LocalUserIdTracker
import domain.models.applications.CollaboratorRole
import domain.models.apidefinitions.APISubscriptionStatus
import domain.models.apidefinitions.ApiVersionDefinition
import controllers.models.ApiSubscriptionsFlow
import play.api.mvc.MessagesRequest
import play.api.i18n.DefaultMessagesApi
import domain.models.apidefinitions.ApiIdentifier
import domain.models.apidefinitions.APIStatus
import domain.models.applications.Application
import domain.models.applications.Environment

class TestController(
    val cookieSigner: CookieSigner,
    val mcc: MessagesControllerComponents,
    val sessionService: SessionService,
    val errorHandler: ErrorHandler,
    val applicationService: ApplicationService,
    val applicationActionService: ApplicationActionService
  )(implicit val ec: ExecutionContext,
    val appConfig: ApplicationConfig)
    extends ApplicationController(mcc) {}

class ActionBuildersSpec
    extends BaseControllerSpec
    with ApplicationServiceMock
    with ApplicationActionServiceMock
    with builder.ApplicationBuilder
    with LocalUserIdTracker
    with builder.SubscriptionsBuilder
    with LoggedInRequestTestHelper {

  trait Setup {
    val loggedInDeveloper = DeveloperSession(session)

    val errorHandler: ErrorHandler = app.injector.instanceOf[ErrorHandler]

    val applicationWithSubscriptionData = buildApplicationWithSubscriptionData(
      developer.email
    )
    val subscriptionWithoutSubFields = buildAPISubscriptionStatus("api name")

    val subscriptionWithSubFields =
      buildAPISubscriptionStatus(
        "api name",
        fields = Some(
          buildSubscriptionFieldsWrapper(
            applicationWithSubscriptionData.application,
            List(buildSubscriptionFieldValue("field1"))
          )
        )
      )

    val underTest = new TestController(
      cookieSigner,
      mcc,
      sessionServiceMock,
      errorHandler,
      applicationServiceMock,
      applicationActionServiceMock
    )

    fetchByApplicationIdReturns(applicationWithSubscriptionData)

    def runTestAction(
        context: ApiContext,
        version: ApiVersion,
        expectedStatus: Int
      ) = {
      val testResultBody = "was called"

      val result = underTest.subFieldsDefinitionsExistActionByApi(
        applicationWithSubscriptionData.application.id,
        context,
        version
      ) { definitionsRequest: ApplicationWithSubscriptionFields[AnyContent] =>
        Future.successful(underTest.Ok(testResultBody))
      }(loggedInRequest)
      status(result) shouldBe expectedStatus
      if (expectedStatus == OK) {
        contentAsString(result) shouldBe testResultBody
      } else {
        contentAsString(result) should not be testResultBody
      }
    }

  }

  trait FraudPreventionSetup extends Setup {

    val fraudPreventionApp = applicationWithSubscriptionData.application
    val fraudPreventionSandboxApp = applicationWithSubscriptionData.application.copy(deployedTo = Environment.SANDBOX)

    def buildApplicationRequest(application: Application, subscriptions: List[APISubscriptionStatus] = List.empty): ApplicationRequest[AnyContent] = {

      ApplicationRequest(
        application,
        application.deployedTo,
        subscriptions = subscriptions,
        Map.empty,
        loggedInDeveloper.email.asAdministratorCollaborator.role,
        loggedInDeveloper,
        new MessagesRequest(
          loggedInRequest.withSession(sessionSubscriptions),
          new DefaultMessagesApi
        )
      )
    }

    val apiIdentifier1 =
      ApiIdentifier(ApiContext("test-api-context-1"), ApiVersion("1.0"))

    val apiIdentifier2 =
      ApiIdentifier(ApiContext("test-api-context-2"), ApiVersion("1.0"))

    val emptyFields = emptySubscriptionFieldsWrapper(
      fraudPreventionApp.id,
      fraudPreventionApp.clientId,
      apiIdentifier1.context,
      apiIdentifier1.version
    )

    val testAPISubscriptionStatus1 = APISubscriptionStatus(
      "test-api-1",
      "api-example-microservice",
      apiIdentifier1.context,
      ApiVersionDefinition(apiIdentifier1.version, APIStatus.STABLE),
      subscribed = true,
      requiresTrust = false,
      fields = emptyFields
    )

    val testFlow = ApiSubscriptionsFlow(Map(apiIdentifier1 -> true))

    val sessionSubscriptions =
      "subscriptions" -> ApiSubscriptionsFlow.toSessionString(testFlow)

    val applicationRequestWithSubs: ApplicationRequest[AnyContent] = buildApplicationRequest(fraudPreventionApp, List(testAPISubscriptionStatus1))
    val applicationRequestWithoutSubs: ApplicationRequest[AnyContent] = buildApplicationRequest(fraudPreventionApp)
    val applicationRequestForSandboxApp: ApplicationRequest[AnyContent] = buildApplicationRequest(fraudPreventionSandboxApp, List(testAPISubscriptionStatus1))

  }

  "subFieldsDefinitionsExistActionByApi" should {
    "Found one" in new Setup {
      givenApplicationAction(applicationWithSubscriptionData, loggedInDeveloper, List(subscriptionWithSubFields))

      runTestAction(subscriptionWithSubFields.context, subscriptionWithSubFields.apiVersion.version, OK)
    }

    "Wrong context" in new Setup {
      givenApplicationAction(applicationWithSubscriptionData, loggedInDeveloper, List(subscriptionWithSubFields))

      runTestAction(ApiContext("wrong-context"), subscriptionWithSubFields.apiVersion.version, NOT_FOUND)
    }

    "Wrong version" in new Setup {
      givenApplicationAction(applicationWithSubscriptionData, loggedInDeveloper, List(subscriptionWithSubFields))

      runTestAction(subscriptionWithSubFields.context, ApiVersion("wrong-version"), NOT_FOUND)
    }

    "Subscription with no fields" in new Setup {
      givenApplicationAction(applicationWithSubscriptionData, loggedInDeveloper, List(subscriptionWithoutSubFields))

      runTestAction(subscriptionWithSubFields.context, subscriptionWithSubFields.apiVersion.version, NOT_FOUND)
    }
  }

  "hasFraudPreventionHeaders" should {

    "return false when appConfig.fraudPreventionApis has no apis" in new FraudPreventionSetup {
      when(appConfig.fraudPreventionApis).thenReturn(List.empty)
      givenApplicationAction(applicationWithSubscriptionData, loggedInDeveloper, List(subscriptionWithSubFields))
      underTest.hasFraudPreventionHeaders(applicationRequestWithSubs) shouldBe false

    }
    "return false when application has no subscriptions" in new FraudPreventionSetup {
      when(appConfig.fraudPreventionApis).thenReturn(List("api-1", "api-2"))
      givenApplicationAction(applicationWithSubscriptionData, loggedInDeveloper, List(subscriptionWithSubFields))
      underTest.hasFraudPreventionHeaders(applicationRequestWithoutSubs) shouldBe false

    }    
    
    "return true when production application has fraud prevention subscriptions" in new FraudPreventionSetup {
      when(appConfig.fraudPreventionApis).thenReturn(List("api-example-microservice"))
      givenApplicationAction(applicationWithSubscriptionData, loggedInDeveloper, List(subscriptionWithSubFields))
      underTest.hasFraudPreventionHeaders(applicationRequestWithSubs) shouldBe true

    }
  }
}
