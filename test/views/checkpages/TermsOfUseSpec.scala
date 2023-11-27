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

package views.checkpages

import java.time.{LocalDateTime, ZoneOffset}

import org.jsoup.Jsoup
import views.helper.CommonViewSpec
import views.html.checkpages.TermsOfUseView

import play.api.mvc.{AnyContentAsEmpty, Call}
import play.api.test.FakeRequest

import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.{CheckInformation, TermsOfUseAgreement, ApplicationState,State}
import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress.StringSyntax
import uk.gov.hmrc.apiplatform.modules.common.domain.models.{ApplicationId, ClientId, Environment}
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder._
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ApplicationConfig
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.TermsOfUseForm
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.TermsOfUseVersion
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.controllers.ApplicationViewModel
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.LoggedInState
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils._

class TermsOfUseSpec extends CommonViewSpec with WithCSRFAddToken with CollaboratorTracker with LocalUserIdTracker with DeveloperSessionBuilder with DeveloperTestData {

  val termsOfUse = app.injector.instanceOf[TermsOfUseView]

  "Terms of use view" must {
    val thirdPartyApplication =
      Application(
        ApplicationId.random,
        ClientId("CLIENT_ID"),
        "APPLICATION NAME",
        LocalDateTime.now(ZoneOffset.UTC),
        Some(LocalDateTime.now(ZoneOffset.UTC)),
        None,
        grantLength,
        Environment.PRODUCTION,
        Some("APPLICATION DESCRIPTION"),
        Set("sample@example.com".toLaxEmail.asAdministratorCollaborator, "someone@example.com".toLaxEmail.asDeveloperCollaborator),
        Standard(),
        ApplicationState(State.TESTING, None, None, None, LocalDateTime.now(ZoneOffset.UTC))
      )

    "show terms of use agreement page that requires terms of use to be agreed" in {
      implicit val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest().withCSRFToken

      val checkInformation = CheckInformation()

      val termsOfUseForm = TermsOfUseForm.fromCheckInformation(checkInformation)
      val developer      = standardDeveloper.loggedIn

      val page = termsOfUse.render(
        ApplicationViewModel(thirdPartyApplication, false, false),
        form = TermsOfUseForm.form.fill(termsOfUseForm),
        submitButtonLabel = "A Label",
        submitAction = mock[Call],
        landingPageRoute = mock[Call],
        TermsOfUseVersion.latest,
        request,
        developer,
        messagesProvider,
        appConfig
      )
      page.contentType should include("text/html")

      val document = Jsoup.parse(page.body)
      document.getElementById("termsOfUseAgreed") shouldNot be(null)
      document.getElementById("termsOfUseAgreed").attr("checked") shouldNot be("checked")
    }

    "show terms of use agreement page that already has the correct terms of use agreed" in {
      implicit val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest().withCSRFToken

      val appConfigMock       = mock[ApplicationConfig]
      val termsOfUseAgreement = TermsOfUseAgreement("email@example.com".toLaxEmail, LocalDateTime.now(ZoneOffset.UTC), "1.0")

      val checkInformation = CheckInformation(termsOfUseAgreements = List(termsOfUseAgreement))

      val termsOfUseForm = TermsOfUseForm.fromCheckInformation(checkInformation)
      val developer      = standardDeveloper.loggedIn

      val page = termsOfUse.render(
        ApplicationViewModel(thirdPartyApplication.copy(checkInformation = Some(checkInformation)), false, false),
        form = TermsOfUseForm.form.fill(termsOfUseForm),
        submitButtonLabel = "A Label",
        submitAction = mock[Call],
        landingPageRoute = mock[Call],
        TermsOfUseVersion.latest,
        request,
        developer,
        implicitly,
        appConfigMock
      )
      page.contentType should include("text/html")

      page.body.contains("Terms of use agreed by email@example.com") shouldBe true
    }
  }
}
