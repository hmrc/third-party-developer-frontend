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

package uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.manageapplication

import org.jsoup.Jsoup
import play.api.test.Helpers._
import uk.gov.hmrc.apiplatform.modules.applications.access.domain.models.Access
import uk.gov.hmrc.apiplatform.modules.applications.common.domain.models.FullName
import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.ApplicationWithCollaboratorsFixtures
import uk.gov.hmrc.apiplatform.modules.applications.submissions.domain.models._
import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress.StringSyntax
import uk.gov.hmrc.apiplatform.modules.common.domain.models.UserId
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.{BaseControllerSpec, routes}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain._
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.service._
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.ViewHelpers._
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithCSRFAddToken
import views.html._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class UpdatePrivacyPolicyLocationControllerSpec
    extends BaseControllerSpec
    with WithCSRFAddToken
    with ApplicationWithCollaboratorsFixtures {

  "changing privacy policy location for old journey applications" should {

    "display update page with url field populated" in new Setup {
      val appWithPrivPolicyUrl = legacyAppWithPrivacyPolicyLocation(Some(privacyPolicyUrl))
      givenApplicationAction(appWithPrivPolicyUrl, adminSession)

      val result = addToken(underTest.updatePrivacyPolicyLocation(appWithPrivPolicyUrl.id))(loggedInAdminRequest)

      status(result) shouldBe OK
      val document = Jsoup.parse(contentAsString(result))
      elementIdentifiedByIdContainsValue(document, "privacyPolicyUrl", privacyPolicyUrl)
      elementExistsById(document, "privacyPolicyInDesktop") shouldBe false
      elementExistsById(document, "privacyPolicyHasUrl") shouldBe false
    }

    "display update page with url field empty if app has no privacy policy" in new Setup {
      val appWithPrivPolicyUrl = legacyAppWithPrivacyPolicyLocation(None)
      givenApplicationAction(appWithPrivPolicyUrl, adminSession)

      val result = addToken(underTest.updatePrivacyPolicyLocation(appWithPrivPolicyUrl.id))(loggedInAdminRequest)

      status(result) shouldBe OK
      val document = Jsoup.parse(contentAsString(result))
      elementIdentifiedByIdContainsValue(document, "privacyPolicyUrl", "")
      elementExistsById(document, "privacyPolicyInDesktop") shouldBe false
      elementExistsById(document, "privacyPolicyHasUrl") shouldBe false
    }

    "update location if form data is valid and return to app details page" in new Setup {
      val newPrivacyPolicyUrl  = "http://example.com/new-priv-policy"
      val appWithPrivPolicyUrl = legacyAppWithPrivacyPolicyLocation(Some(privacyPolicyUrl))
      givenApplicationAction(appWithPrivPolicyUrl, adminSession)
      when(applicationServiceMock.updatePrivacyPolicyLocation(eqTo(appWithPrivPolicyUrl), *[UserId], eqTo(PrivacyPolicyLocations.Url(newPrivacyPolicyUrl)))(*))
        .thenReturn(Future.successful(ApplicationUpdateSuccessful))

      private val request = loggedInAdminRequest.withFormUrlEncodedBody("privacyPolicyUrl" -> newPrivacyPolicyUrl, "isInDesktop" -> "false", "isNewJourney" -> "false")

      val result = addToken(underTest.updatePrivacyPolicyLocationAction(appWithPrivPolicyUrl.id))(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(routes.MainApplicationDetailsController.applicationDetails(appWithPrivPolicyUrl.id).url)
    }
  }

  "changing privacy policy location for new journey applications" should {

    "display update page with 'in desktop' radio selected" in new Setup {
      val appWithPrivPolicyInDesktop = appWithPrivacyPolicyLocation(PrivacyPolicyLocations.InDesktopSoftware)
      givenApplicationAction(appWithPrivPolicyInDesktop, adminSession)

      val result = addToken(underTest.updatePrivacyPolicyLocation(appWithPrivPolicyInDesktop.id))(loggedInAdminRequest)

      status(result) shouldBe OK
      val document = Jsoup.parse(contentAsString(result))
      elementExistsByIdWithAttr(document, "privacyPolicyInDesktop", "checked") shouldBe true
      elementExistsByIdWithAttr(document, "privacyPolicyHasUrl", "checked") shouldBe false
    }

    "display update page with 'url' radio selected" in new Setup {
      val appWithPrivPolicyUrl = appWithPrivacyPolicyLocation(PrivacyPolicyLocations.Url(privacyPolicyUrl))
      givenApplicationAction(appWithPrivPolicyUrl, adminSession)

      val result = addToken(underTest.updatePrivacyPolicyLocation(appWithPrivPolicyUrl.id))(loggedInAdminRequest)

      status(result) shouldBe OK
      val document = Jsoup.parse(contentAsString(result))
      elementExistsByIdWithAttr(document, "privacyPolicyInDesktop", "checked") shouldBe false
      elementExistsByIdWithAttr(document, "privacyPolicyHasUrl", "checked") shouldBe true
      elementIdentifiedByIdContainsValue(document, "privacyPolicyUrl", privacyPolicyUrl)
    }

    "return See Other if application cannot be retrieved" in new Setup {
      val appWithPrivPolicyInDesktop = appWithPrivacyPolicyLocation(PrivacyPolicyLocations.InDesktopSoftware)
      givenApplicationActionReturnsNotFound(appWithPrivPolicyInDesktop.id)

      val result = addToken(underTest.updatePrivacyPolicyLocation(appWithPrivPolicyInDesktop.id))(loggedInAdminRequest)

      status(result) shouldBe SEE_OTHER
    }

    "return bad request if privacy policy location has not changed" in new Setup {
      val appWithPrivPolicyInDesktop = appWithPrivacyPolicyLocation(PrivacyPolicyLocations.InDesktopSoftware)
      givenApplicationAction(appWithPrivPolicyInDesktop, adminSession)

      private val request = loggedInAdminRequest.withFormUrlEncodedBody("privacyPolicyUrl" -> "", "isInDesktop" -> "true", "isNewJourney" -> "true")
      val result          = addToken(underTest.updatePrivacyPolicyLocationAction(appWithPrivPolicyInDesktop.id))(request)

      status(result) shouldBe BAD_REQUEST
    }

    "return bad request if form data is invalid" in new Setup {
      val appWithPrivPolicyInDesktop = appWithPrivacyPolicyLocation(PrivacyPolicyLocations.InDesktopSoftware)
      givenApplicationAction(appWithPrivPolicyInDesktop, adminSession)

      private val request = loggedInAdminRequest.withFormUrlEncodedBody("privacyPolicyUrl" -> "", "isInDesktop" -> "false", "isNewJourney" -> "true")
      val result          = addToken(underTest.updatePrivacyPolicyLocationAction(appWithPrivPolicyInDesktop.id))(request)

      status(result) shouldBe BAD_REQUEST
    }

    "update location if form data is valid and return to app details page" in new Setup {
      val appWithPrivPolicyInDesktop = appWithPrivacyPolicyLocation(PrivacyPolicyLocations.InDesktopSoftware)
      givenApplicationAction(appWithPrivPolicyInDesktop, adminSession)
      when(applicationServiceMock.updatePrivacyPolicyLocation(eqTo(appWithPrivPolicyInDesktop), *[UserId], eqTo(PrivacyPolicyLocations.Url(privacyPolicyUrl)))(*))
        .thenReturn(Future.successful(ApplicationUpdateSuccessful))

      private val request = loggedInAdminRequest.withFormUrlEncodedBody("privacyPolicyUrl" -> privacyPolicyUrl, "isInDesktop" -> "false", "isNewJourney" -> "true")
      val result          = addToken(underTest.updatePrivacyPolicyLocationAction(appWithPrivPolicyInDesktop.id))(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(routes.MainApplicationDetailsController.applicationDetails(appWithPrivPolicyInDesktop.id).url)
    }
  }

  trait Setup
      extends ApplicationServiceMock
      with ApplicationActionServiceMock {

    val updatePrivacyPolicyLocationView = app.injector.instanceOf[UpdatePrivacyPolicyLocationView]

    val underTest = new UpdatePrivacyPolicyLocationController(
      mockErrorHandler,
      applicationServiceMock,
      applicationActionServiceMock,
      sessionServiceMock,
      mcc,
      cookieSigner,
      updatePrivacyPolicyLocationView
    )

    val privacyPolicyUrl = "http://example.com/priv-policy"

    def legacyAppWithPrivacyPolicyLocation(maybePrivacyPolicyUrl: Option[String]) =
      standardApp.withAccess(Access.Standard(List.empty, List.empty, None, maybePrivacyPolicyUrl, Set.empty, None, None))

    def appWithPrivacyPolicyLocation(privacyPolicyLocation: PrivacyPolicyLocation) = standardApp.withAccess(
      Access.Standard(
        List.empty,
        List.empty,
        None,
        None,
        Set.empty,
        None,
        Some(
          ImportantSubmissionData(
            None,
            ResponsibleIndividual(FullName("bob example"), "bob@example.com".toLaxEmail),
            Set.empty,
            TermsAndConditionsLocations.InDesktopSoftware,
            privacyPolicyLocation,
            List.empty
          )
        )
      )
    )
  }
}
