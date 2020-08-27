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

// TODO - test XZY
//
// /*
//  * Copyright 2020 HM Revenue & Customs
//  *
//  * Licensed under the Apache License, Version 2.0 (the "License");
//  * you may not use this file except in compliance with the License.
//  * You may obtain a copy of the License at
//  *
//  *     http://www.apache.org/licenses/LICENSE-2.0
//  *
//  * Unless required by applicable law or agreed to in writing, software
//  * distributed under the License is distributed on an "AS IS" BASIS,
//  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  * See the License for the specific language governing permissions and
//  * limitations under the License.
//  */

// package controllers

// import config.{ApplicationConfig, ErrorHandler}
// import domain.models.apidefinitions.{ApiContext, ApiVersion}
// import helpers.LoggedInRequestTestHelper
// import mocks.service._
// import play.api.http.Status.{NOT_FOUND, OK}
// import play.api.libs.crypto.CookieSigner
// import play.api.mvc.{AnyContent, MessagesControllerComponents}
// import play.api.test.Helpers._
// import service.{ApplicationService, SessionService}

// import scala.concurrent.{ExecutionContext, Future}
// import scala.concurrent.ExecutionContext.Implicits.global
// import service.ApplicationActionService

// class TestController(
//     val cookieSigner: CookieSigner,
//     val mcc: MessagesControllerComponents,
//     val sessionService: SessionService,
//     val errorHandler: ErrorHandler,
//     val applicationService: ApplicationService,
//     val applicationActionService: ApplicationActionService
// )(implicit val ec: ExecutionContext, val appConfig: ApplicationConfig)
//     extends ApplicationController(mcc) {}

// class ActionBuildersSpec extends BaseControllerSpec with ApplicationServiceMock with ApplicationActionServiceMock with builder.ApplicationBuilder with builder.SubscriptionsBuilder with LoggedInRequestTestHelper {

//   trait Setup {
//     val errorHandler: ErrorHandler = app.injector.instanceOf[ErrorHandler]

//     val applicationWithSubscriptionData = buildApplicationWithSubscriptionData(developer.email)
//     val subscriptionWithoutSubFields = buildAPISubscriptionStatus("api name")
//     val subscriptionWithSubFields =
//       buildAPISubscriptionStatus("api name", fields = Some(buildSubscriptionFieldsWrapper(applicationWithSubscriptionData.application, Seq(buildSubscriptionFieldValue("field1")))))

//     val underTest = new TestController(cookieSigner, mcc, sessionServiceMock, errorHandler, applicationServiceMock, applicationActionServiceMock)

//     fetchByApplicationIdReturns(applicationWithSubscriptionData)

//     def runTestAction(context: ApiContext, version: ApiVersion, expectedStatus: Int) = {
//       val testResultBody = "was called"

//       val result = underTest.subFieldsDefinitionsExistActionByApi(applicationWithSubscriptionData.application.id, context, version) {
//         definitionsRequest: ApplicationWithSubscriptionFields[AnyContent] => Future.successful(underTest.Ok(testResultBody))
//       }(loggedInRequest)
//       status(result) shouldBe expectedStatus
//       if (expectedStatus == OK) {
//         contentAsString(result) shouldBe testResultBody
//       } else {
//         contentAsString(result) should not be testResultBody
//       }
//     }
//   }

//   "subFieldsDefinitionsExistActionByApi" should {
//     "Found one" in new Setup {
//       givenApplicationHasSubs(applicationWithSubscriptionData.application, Seq(subscriptionWithSubFields))

//       runTestAction(subscriptionWithSubFields.context, subscriptionWithSubFields.apiVersion.version, OK)
//     }

//     "Wrong context" in new Setup {
//       givenApplicationHasSubs(applicationWithSubscriptionData.application, Seq(subscriptionWithSubFields))

//       runTestAction(ApiContext("wrong-context"), subscriptionWithSubFields.apiVersion.version, NOT_FOUND)
//     }

//     "Wrong version" in new Setup {
//       givenApplicationHasSubs(applicationWithSubscriptionData.application, Seq(subscriptionWithSubFields))

//       runTestAction(subscriptionWithSubFields.context, ApiVersion("wrong-version"), NOT_FOUND)
//     }

//     "Subscription with no fields" in new Setup {
//       givenApplicationHasSubs(applicationWithSubscriptionData.application, Seq(subscriptionWithoutSubFields))

//       runTestAction(subscriptionWithSubFields.context, subscriptionWithSubFields.apiVersion.version, NOT_FOUND)
//     }
//   }
// }
