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

import config.{ApplicationConfig, ErrorHandler}
import helpers.LoggedInRequestTestHelper
import mocks.service.ApplicationServiceMock
import play.api.http.Status.{NOT_FOUND, OK}
import play.api.libs.crypto.CookieSigner
import play.api.mvc.{AnyContent, MessagesControllerComponents}
import service.{ApplicationService, SessionService}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class TestController( val cookieSigner: CookieSigner,
                      val mcc: MessagesControllerComponents,
                      val sessionService: SessionService,
                      val errorHandler: ErrorHandler,
                      val applicationService: ApplicationService)
                      (implicit val ec: ExecutionContext, val appConfig: ApplicationConfig) extends ApplicationController(mcc) {}

class ActionBuildersSpec extends BaseControllerSpec
  with ApplicationServiceMock
  with builder.ApplicationBuilder
  with builder.SubscriptionsBuilder
  with LoggedInRequestTestHelper {

  trait Setup {
    val errorHandler: ErrorHandler = app.injector.instanceOf[ErrorHandler]

    val application = buildApplication(developer.email)
    val subscriptionWithoutSubFields = buildAPISubscriptionStatus("api name")
    val subscriptionWithSubFields = buildAPISubscriptionStatus(
        "api name",
        fields = Some(buildSubscriptionFieldsWrapper(application,Seq(buildSubscriptionFieldValue("field1")))))
  
    val underTest = new TestController(cookieSigner, mcc, sessionServiceMock, errorHandler, applicationServiceMock)

    fetchByApplicationIdReturns(application)

    def runTestAction(context: String, version: String, expectedStatus: Int) = {
      val testResultBody = "was called"
     
      val result = await(underTest.subFieldsDefinitionsExistActionByApi(application.id, context, version) {
        definitionsRequest: ApplicationWithSubscriptionFields[AnyContent] =>
            Future.successful(underTest.Ok(testResultBody))
      }(loggedInRequest))
        
      status(result) shouldBe expectedStatus
      if (expectedStatus == OK) {
        bodyOf(result) shouldBe testResultBody
      } else {
        bodyOf(result) should not be testResultBody
      }
    }
  }
  
  "subFieldsDefinitionsExistActionByApi" should {
    "Found one" in new Setup {
      givenApplicationHasSubs(application, Seq(subscriptionWithSubFields))

      runTestAction(subscriptionWithSubFields.context, subscriptionWithSubFields.apiVersion.version, OK)
    }

    "Wrong context" in new Setup {
      givenApplicationHasSubs(application, Seq(subscriptionWithSubFields))

      runTestAction("wrong-context", subscriptionWithSubFields.apiVersion.version, NOT_FOUND)
    }

    "Wrong version" in new Setup {
      givenApplicationHasSubs(application, Seq(subscriptionWithSubFields))

      runTestAction(subscriptionWithSubFields.context, "wrong-version", NOT_FOUND)
    }

    "Subscription with no fields" in new Setup {
      givenApplicationHasSubs(application, Seq(subscriptionWithoutSubFields))

      runTestAction(subscriptionWithSubFields.context, subscriptionWithSubFields.apiVersion.version, NOT_FOUND)
    }
  }
}
