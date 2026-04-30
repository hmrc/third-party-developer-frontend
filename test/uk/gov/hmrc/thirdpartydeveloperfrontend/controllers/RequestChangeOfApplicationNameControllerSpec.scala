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

import play.api.test.Helpers._
import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models._
import uk.gov.hmrc.apiplatform.modules.applications.core.interface.models.ApplicationNameValidationResult
import uk.gov.hmrc.apiplatform.modules.common.domain.models.{LaxEmailAddress, UserId}
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.service._
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithCSRFAddToken
import views.html._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class RequestChangeOfApplicationNameControllerSpec
    extends BaseControllerSpec
    with WithCSRFAddToken
    with ApplicationWithCollaboratorsFixtures {

  val approvedApplication = standardApp.withAccess(standardAccessOne).modify(_.copy(description = Some("Some App Description")))

  "changeOfApplicationName" should {
    "show page successfully" in new Setup {
      givenApplicationAction(approvedApplication, adminSession)

      val result = addToken(underTest.requestChangeOfAppName(approvedApplication.id))(loggedInAdminRequest)

      status(result) shouldBe OK
      contentAsString(result) should include("Application name")
    }

    "return forbidden when not an admin" in new Setup {
      givenApplicationAction(approvedApplication, devSession)

      val result = addToken(underTest.requestChangeOfAppName(approvedApplication.id))(loggedInDevRequest)

      status(result) shouldBe FORBIDDEN
    }
  }

  "changeOfApplicationNameAction" should {
    "show success page if name changed successfully" in new Setup {
      givenApplicationAction(approvedApplication, adminSession)

      when(underTest.applicationService.requestProductonApplicationNameChange(*[UserId], *, *[ApplicationName], *, *[LaxEmailAddress])(*))
        .thenReturn(Future.successful(Some("ref")))

      private val request = loggedInAdminRequest.withFormUrlEncodedBody("applicationName" -> "Legal new app name")

      val result = addToken(underTest.requestChangeOfAppNameAction(approvedApplication.id))(request)

      status(result) shouldBe OK
      verify(underTest.applicationService).requestProductonApplicationNameChange(*[UserId], *, *[ApplicationName], *, *[LaxEmailAddress])(*)
      contentAsString(result) should include("We have received your request to change the application name to")
    }

    "show error if application name is not valid" in new Setup {
      givenApplicationAction(approvedApplication, adminSession)
      when(underTest.applicationService.isApplicationNameValid(*, *, *)(*))
        .thenReturn(Future.successful(ApplicationNameValidationResult.Invalid))

      private val request = loggedInAdminRequest.withFormUrlEncodedBody("applicationName" -> "HMRC - Illegal new app name")

      val result = addToken(underTest.requestChangeOfAppNameAction(approvedApplication.id))(request)

      status(result) shouldBe BAD_REQUEST
      contentAsString(result) should include("Application name must not include HMRC or HM Revenue and Customs")
    }

    "show error if application name is too short" in new Setup {
      givenApplicationAction(approvedApplication, adminSession)

      private val request = loggedInAdminRequest.withFormUrlEncodedBody("applicationName" -> "")

      val result = addToken(underTest.requestChangeOfAppNameAction(approvedApplication.id))(request)

      status(result) shouldBe BAD_REQUEST
      contentAsString(result) should include("Application name must be between 2 and 50 characters and only use ASCII characters excluding")
    }

    "show error if application name contains non-allowed chars" in new Setup {
      givenApplicationAction(approvedApplication, adminSession)

      private val request = loggedInAdminRequest.withFormUrlEncodedBody("applicationName" -> "name£")

      val result = addToken(underTest.requestChangeOfAppNameAction(approvedApplication.id))(request)

      status(result) shouldBe BAD_REQUEST
      contentAsString(result) should include("Application name must be between 2 and 50 characters and only use ASCII characters excluding")
    }

    "show error if new application name is the same as the old one" in new Setup {
      givenApplicationAction(approvedApplication, adminSession)

      private val request = loggedInAdminRequest.withFormUrlEncodedBody("applicationName" -> approvedApplication.name.value)

      val result = addToken(underTest.requestChangeOfAppNameAction(approvedApplication.id))(request)

      status(result) shouldBe BAD_REQUEST
      contentAsString(result) should include("The application already has the specified name")
    }

    "show forbidden if not an admin" in new Setup {
      givenApplicationAction(approvedApplication, devSession)

      private val request = loggedInDevRequest.withFormUrlEncodedBody("applicationName" -> "new app name")

      val result = addToken(underTest.requestChangeOfAppNameAction(approvedApplication.id))(request)

      status(result) shouldBe FORBIDDEN
    }
  }

  trait Setup
      extends ApplicationServiceMock
      with ApplicationActionServiceMock {

    val requestChangeOfApplicationNameView      = app.injector.instanceOf[RequestChangeOfApplicationNameView]
    val changeOfApplicationNameConfirmationView = app.injector.instanceOf[ChangeOfApplicationNameConfirmationView]

    val underTest = new RequestChangeOfApplicationNameController(
      mockErrorHandler,
      applicationServiceMock,
      applicationActionServiceMock,
      sessionServiceMock,
      mcc,
      cookieSigner,
      requestChangeOfApplicationNameView,
      changeOfApplicationNameConfirmationView
    )

    when(underTest.applicationService.isApplicationNameValid(*, *, *)(*))
      .thenReturn(Future.successful(ApplicationNameValidationResult.Valid))
  }
}
