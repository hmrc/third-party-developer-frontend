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
import scala.concurrent.{ExecutionContext, Future}

import play.api.http.Status.{NOT_FOUND, OK}
import play.api.libs.crypto.CookieSigner
import play.api.mvc.{AnyContent, MessagesControllerComponents}
import play.api.test.Helpers._

import uk.gov.hmrc.apiplatform.modules.common.domain.models._
import uk.gov.hmrc.apiplatform.modules.tpd.test.utils.LocalUserIdTracker
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder._
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.{ApplicationConfig, ErrorHandler}
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.actions.SubscriptionFieldsActions
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.session.DeveloperSession
import uk.gov.hmrc.thirdpartydeveloperfrontend.helpers.LoggedInRequestTestHelper
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.service._
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.{ApplicationActionService, ApplicationService, SessionService}

class TestController(
    val cookieSigner: CookieSigner,
    val mcc: MessagesControllerComponents,
    val sessionService: SessionService,
    val errorHandler: ErrorHandler,
    val applicationService: ApplicationService,
    val applicationActionService: ApplicationActionService
  )(implicit val ec: ExecutionContext,
    val appConfig: ApplicationConfig
  ) extends ApplicationController(mcc)
    with SubscriptionFieldsActions {}

class ActionBuildersSpec extends BaseControllerSpec
    with ApplicationServiceMock
    with ApplicationActionServiceMock
    with ApplicationBuilder
    with LocalUserIdTracker
    with SubscriptionsBuilder
    with LoggedInRequestTestHelper {

  trait Setup {
    val loggedInDeveloper = DeveloperSession(session)

    val errorHandler: ErrorHandler = app.injector.instanceOf[ErrorHandler]

    val applicationWithSubscriptionData = buildApplicationWithSubscriptionData(developer.email)
    val subscriptionWithoutSubFields    = buildAPISubscriptionStatus("api name")

    val subscriptionWithSubFields =
      buildAPISubscriptionStatus("api name", fields = Some(buildSubscriptionFieldsWrapper(applicationWithSubscriptionData.application, List(buildSubscriptionFieldValue("field1")))))

    val underTest = new TestController(cookieSigner, mcc, sessionServiceMock, errorHandler, applicationServiceMock, applicationActionServiceMock)

    fetchByApplicationIdReturns(applicationWithSubscriptionData)

    def runTestAction(context: ApiContext, version: ApiVersionNbr, expectedStatus: Int) = {
      val testResultBody = "was called"

      val result = underTest.subFieldsDefinitionsExistActionByApi(applicationWithSubscriptionData.application.id, context, version) {
        definitionsRequest: ApplicationWithSubscriptionFieldsRequest[AnyContent] => Future.successful(underTest.Ok(testResultBody))
      }(loggedInRequest)
      status(result) shouldBe expectedStatus
      if (expectedStatus == OK) {
        contentAsString(result) shouldBe testResultBody
      } else {
        contentAsString(result) should not be testResultBody
      }
    }
  }

  "subFieldsDefinitionsExistActionByApi" should {
    "Found one" in new Setup {
      givenApplicationAction(applicationWithSubscriptionData, loggedInDeveloper, List(subscriptionWithSubFields))

      runTestAction(subscriptionWithSubFields.context, subscriptionWithSubFields.apiVersion.versionNbr, OK)
    }

    "Wrong context" in new Setup {
      givenApplicationAction(applicationWithSubscriptionData, loggedInDeveloper, List(subscriptionWithSubFields))

      runTestAction(ApiContext("wrong-context"), subscriptionWithSubFields.apiVersion.versionNbr, NOT_FOUND)
    }

    "Wrong version" in new Setup {
      givenApplicationAction(applicationWithSubscriptionData, loggedInDeveloper, List(subscriptionWithSubFields))

      runTestAction(subscriptionWithSubFields.context, ApiVersionNbr("wrong-version"), NOT_FOUND)
    }

    "Subscription with no fields" in new Setup {
      givenApplicationAction(applicationWithSubscriptionData, loggedInDeveloper, List(subscriptionWithoutSubFields))

      runTestAction(subscriptionWithSubFields.context, subscriptionWithSubFields.apiVersion.versionNbr, NOT_FOUND)
    }
  }
}
