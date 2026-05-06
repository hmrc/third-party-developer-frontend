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

import org.jsoup.Jsoup
import views.html.UpdateTermsAndConditionsLocationView

import play.api.test.Helpers._

import uk.gov.hmrc.apiplatform.modules.applications.access.domain.models.Access
import uk.gov.hmrc.apiplatform.modules.applications.common.domain.models.FullName
import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.ApplicationWithCollaboratorsFixtures
import uk.gov.hmrc.apiplatform.modules.applications.submissions.domain.models._
import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress.StringSyntax
import uk.gov.hmrc.apiplatform.modules.common.domain.models.UserId
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.ApplicationUpdateSuccessful
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.service._
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.ViewHelpers._
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithCSRFAddToken

class UpdateTermsAndConditionsLocationControllerSpec
    extends BaseControllerSpec
    with WithCSRFAddToken
    with ApplicationWithCollaboratorsFixtures {

  "changing terms and conditions location for old journey applications" should {
    "display update page with url field populated" in new Setup {
      val appWithTermsAndConditionsUrl = legacyAppWithTermsAndConditionsLocation(Some(termsAndConditionsUrl))
      givenApplicationAction(appWithTermsAndConditionsUrl, adminSession)

      val result = addToken(underTest.updateTermsAndConditionsLocation(appWithTermsAndConditionsUrl.id))(loggedInAdminRequest)

      status(result) shouldBe OK
      val document = Jsoup.parse(contentAsString(result))
      elementIdentifiedByIdContainsValue(document, "termsAndConditionsUrl", termsAndConditionsUrl)
      elementExistsById(document, "termsAndConditionsInDesktop") shouldBe false
      elementExistsById(document, "termsAndConditionsHasUrl") shouldBe false
    }

    "display update page with url field empty if app has no terms and conditions" in new Setup {
      val appWithTermsAndConditionsUrl = legacyAppWithTermsAndConditionsLocation(None)
      givenApplicationAction(appWithTermsAndConditionsUrl, adminSession)

      val result = addToken(underTest.updateTermsAndConditionsLocation(appWithTermsAndConditionsUrl.id))(loggedInAdminRequest)

      status(result) shouldBe OK
      val document = Jsoup.parse(contentAsString(result))
      elementIdentifiedByIdContainsValue(document, "termsAndConditionsUrl", "")
      elementExistsById(document, "termsAndConditionsInDesktop") shouldBe false
      elementExistsById(document, "termsAndConditionsHasUrl") shouldBe false
    }

    "update location if form data is valid and return to app details page" in new Setup {
      val newTermsAndConditionsUrl     = "http://example.com/new-terms-conds"
      val appWithTermsAndConditionsUrl = legacyAppWithTermsAndConditionsLocation(Some(termsAndConditionsUrl))
      givenApplicationAction(appWithTermsAndConditionsUrl, adminSession)
      when(applicationServiceMock.updateTermsConditionsLocation(eqTo(appWithTermsAndConditionsUrl), *[UserId], eqTo(TermsAndConditionsLocations.Url(newTermsAndConditionsUrl)))(*))
        .thenReturn(Future.successful(ApplicationUpdateSuccessful))

      private val request = loggedInAdminRequest.withFormUrlEncodedBody("termsAndConditionsUrl" -> newTermsAndConditionsUrl, "isInDesktop" -> "false", "isNewJourney" -> "false")
      val result          = addToken(underTest.updateTermsAndConditionsLocationAction(appWithTermsAndConditionsUrl.id))(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(routes.MainApplicationDetailsController.applicationDetails(appWithTermsAndConditionsUrl.id).url)
    }
  }

  "changing terms and conditions location for new journey applications" should {

    "display update page with 'in desktop' radio selected" in new Setup {
      val appWithTermsAndConditionsInDesktop = appWithTermsAndConditionsLocation(TermsAndConditionsLocations.InDesktopSoftware)
      givenApplicationAction(appWithTermsAndConditionsInDesktop, adminSession)

      val result = addToken(underTest.updateTermsAndConditionsLocation(appWithTermsAndConditionsInDesktop.id))(loggedInAdminRequest)

      status(result) shouldBe OK
      val document = Jsoup.parse(contentAsString(result))
      elementExistsByIdWithAttr(document, "termsAndConditionsInDesktop", "checked") shouldBe true
      elementExistsByIdWithAttr(document, "termsAndConditionsHasUrl", "checked") shouldBe false
    }

    "display update page with 'url' radio selected" in new Setup {
      val appWithTermsAndConditionsUrl = appWithTermsAndConditionsLocation(TermsAndConditionsLocations.Url(termsAndConditionsUrl))
      givenApplicationAction(appWithTermsAndConditionsUrl, adminSession)

      val result = addToken(underTest.updateTermsAndConditionsLocation(appWithTermsAndConditionsUrl.id))(loggedInAdminRequest)

      status(result) shouldBe OK
      val document = Jsoup.parse(contentAsString(result))
      elementExistsByIdWithAttr(document, "termsAndConditionsInDesktop", "checked") shouldBe false
      elementExistsByIdWithAttr(document, "termsAndConditionsHasUrl", "checked") shouldBe true
      elementIdentifiedByIdContainsValue(document, "termsAndConditionsUrl", termsAndConditionsUrl)
    }

    "return see other error if application cannot be retrieved" in new Setup {
      val appWithTermsAndConditionsInDesktop = appWithTermsAndConditionsLocation(TermsAndConditionsLocations.InDesktopSoftware)
      givenApplicationActionReturnsNotFound(appWithTermsAndConditionsInDesktop.id)

      val result = addToken(underTest.updateTermsAndConditionsLocationAction(appWithTermsAndConditionsInDesktop.id))(loggedInAdminRequest)

      status(result) shouldBe SEE_OTHER
    }

    "return bad request if terms and conditions location has not changed" in new Setup {
      val appWithTermsAndConditionsInDesktop = appWithTermsAndConditionsLocation(TermsAndConditionsLocations.InDesktopSoftware)
      givenApplicationAction(appWithTermsAndConditionsInDesktop, adminSession)

      private val request = loggedInAdminRequest.withFormUrlEncodedBody("termsAndConditionsUrl" -> "", "isInDesktop" -> "true", "isNewJourney" -> "true")
      val result          = addToken(underTest.updateTermsAndConditionsLocationAction(appWithTermsAndConditionsInDesktop.id))(request)

      status(result) shouldBe BAD_REQUEST
    }

    "return bad request if form data is invalid" in new Setup {
      val appWithTermsAndConditionsInDesktop = appWithTermsAndConditionsLocation(TermsAndConditionsLocations.InDesktopSoftware)
      givenApplicationAction(appWithTermsAndConditionsInDesktop, adminSession)

      private val request = loggedInAdminRequest.withFormUrlEncodedBody("termsAndConditionsUrl" -> "", "isInDesktop" -> "false", "isNewJourney" -> "true")
      val result          = addToken(underTest.updateTermsAndConditionsLocationAction(appWithTermsAndConditionsInDesktop.id))(request)

      status(result) shouldBe BAD_REQUEST
    }

    "update location if form data is valid and return to app details page" in new Setup {
      val appWithTermsAndConditionsInDesktop = appWithTermsAndConditionsLocation(TermsAndConditionsLocations.InDesktopSoftware)
      givenApplicationAction(appWithTermsAndConditionsInDesktop, adminSession)
      when(applicationServiceMock.updateTermsConditionsLocation(eqTo(appWithTermsAndConditionsInDesktop), *[UserId], eqTo(TermsAndConditionsLocations.Url(termsAndConditionsUrl)))(*))
        .thenReturn(Future.successful(ApplicationUpdateSuccessful))

      private val request = loggedInAdminRequest.withFormUrlEncodedBody("termsAndConditionsUrl" -> termsAndConditionsUrl, "isInDesktop" -> "false", "isNewJourney" -> "true")
      val result          = addToken(underTest.updateTermsAndConditionsLocationAction(appWithTermsAndConditionsInDesktop.id))(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(routes.MainApplicationDetailsController.applicationDetails(appWithTermsAndConditionsInDesktop.id).url)
    }
  }

  trait Setup
      extends ApplicationServiceMock
      with ApplicationActionServiceMock {

    val updateTermsAndConditionsLocationView = app.injector.instanceOf[UpdateTermsAndConditionsLocationView]

    val underTest = new UpdateTermsAndConditionsLocationController(
      mockErrorHandler,
      applicationServiceMock,
      applicationActionServiceMock,
      sessionServiceMock,
      mcc,
      cookieSigner,
      clock,
      updateTermsAndConditionsLocationView
    )

    val termsAndConditionsUrl = "http://example.com/terms-conds"

    def legacyAppWithTermsAndConditionsLocation(maybeTermsAndConditionsUrl: Option[String]) =
      standardApp.withAccess(Access.Standard(List.empty, List.empty, maybeTermsAndConditionsUrl, None, Set.empty, None, None))

    def appWithTermsAndConditionsLocation(termsAndConditionsLocation: TermsAndConditionsLocation) = standardApp.withAccess(
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
            termsAndConditionsLocation,
            PrivacyPolicyLocations.InDesktopSoftware,
            List.empty
          )
        )
      )
    )
  }
}
