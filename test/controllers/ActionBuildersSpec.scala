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

import uk.gov.hmrc.play.test.UnitSpec
import org.scalatest.mockito.MockitoSugar
import uk.gov.hmrc.play.test.WithFakeApplication
import config.ApplicationConfig
import service.SessionService
import config.ErrorHandler
import play.api.http.Status.{OK, NOT_FOUND}
import play.api.mvc._
import play.api.test.Helpers._
import play.api.test.FakeRequest
import service.ApplicationService

import scala.concurrent.ExecutionContext.Implicits.global
import play.api.i18n.Messages.Implicits.applicationMessages
import play.api.mvc.Result
import play.api.mvc.AnyContent
import play.api.mvc.Action
import play.api.mvc.Request
import play.api.libs.crypto.CookieSigner
import play.api.i18n.MessagesApi
import scala.concurrent.ExecutionContext
import play.api.mvc.AnyContentAsEmpty
import utils.WithLoggedInSession
import domain.Developer
import domain.Session
import domain.LoggedInState
import utils.WithLoggedInSession._
import play.filters.csrf.CSRF.TokenProvider
import mocks.service.SessionServiceMock
import mocks.service.ApplicationServiceMock
import cats.data.NonEmptyList
import scala.concurrent.Future
import org.scalatest.Suite
import domain.DeveloperSession
import security.DevHubAuthorization

trait LoggedInRequestTestHelper extends SessionServiceMock with WithFakeApplication with DevHubAuthorization {
  this: BaseControllerSpec =>    
    val sessionService = mock[SessionService]

    val developer = Developer("thirdpartydeveloper@example.com", "John", "Doe")
    val sessionId = "sessionId"
    val session = Session(sessionId, developer, LoggedInState.LOGGED_IN)

    fetchSessionByIdReturns(sessionId, session)
   
    private val sessionParams = Seq(
      "csrfToken" -> fakeApplication.injector.instanceOf[TokenProvider].generateToken
    )

    lazy val loggedInRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
      .withLoggedIn(this, implicitly)(sessionId)
      .withSession(sessionParams: _*)
}

class TestController( val cookieSigner: CookieSigner,
                      val messagesApi: MessagesApi,
                      val sessionService: SessionService,
                      val errorHandler: ErrorHandler,
                      val applicationService: ApplicationService)
                      (implicit val ec: ExecutionContext, val appConfig: ApplicationConfig) extends ApplicationController {}

class ActionBuildersSpec extends BaseControllerSpec
  with UnitSpec 
  with MockitoSugar 
  with ApplicationServiceMock
  with builder.ApplicationBuilder
  with builder.SubscriptionsBuilder
  with LoggedInRequestTestHelper {
  trait Setup {
    
    implicit var playApplication = fakeApplication
    
    val errorHandler: ErrorHandler = fakeApplication.injector.instanceOf[ErrorHandler]
    implicit val appConfig: ApplicationConfig = mock[ApplicationConfig]
   
    lazy val messagesApi = fakeApplication.injector.instanceOf[MessagesApi]
    implicit val ec: ExecutionContext = fakeApplication.injector.instanceOf[ExecutionContext]

    val application = buildApplication(developer.email)
    val subscriptionWithoutSubFields = buildAPISubscriptionStatus("api name")
    val subscriptionWithSubFields = buildAPISubscriptionStatus(
        "api name", 
        fields = Some(buildSubscriptionFieldsWrapper(application,NonEmptyList.one(buildSubscriptionFieldValue("field1")))))
  
    val underTest = new TestController(cookieSigner, messagesApi, sessionServiceMock, errorHandler, applicationServiceMock)

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
