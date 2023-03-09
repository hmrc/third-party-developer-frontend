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

package views

import java.time.format.DateTimeFormatter
import java.time.{LocalDateTime, Period, ZoneOffset}

import org.jsoup.Jsoup
import views.helper.CommonViewSpec
import views.html.TermsOfUseView

import play.api.test.FakeRequest
import play.twirl.api.HtmlFormat.Appendable

import uk.gov.hmrc.apiplatform.modules.applications.domain.models.{ApplicationId, ClientId}
import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress.StringSyntax
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.{DeveloperBuilder, DeveloperSessionBuilder}
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.TermsOfUseForm
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.TermsOfUseVersion
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.controllers.ApplicationViewModel
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.LoggedInState
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.{LocalUserIdTracker, WithCSRFAddToken}

class TermsOfUseSpec extends CommonViewSpec
    with WithCSRFAddToken
    with LocalUserIdTracker
    with DeveloperSessionBuilder
    with DeveloperBuilder {

  val termsOfUseView = app.injector.instanceOf[TermsOfUseView]

  case class Page(doc: Appendable) {
    lazy val body          = Jsoup.parse(doc.body)
    lazy val title         = body.title
    lazy val header        = body.getElementById("terms-of-use-header")
    lazy val alert         = body.getElementById("termsOfUseAlert")
    lazy val termsOfUse    = body.getElementById("termsOfUse")
    lazy val agreementForm = body.getElementById("termsOfUseForm")
  }

  "Terms of use view" when {
    implicit val request    = FakeRequest().withCSRFToken
    implicit val loggedIn   = buildDeveloperWithRandomId("developer@example.com".toLaxEmail, "Joe", "Bloggs").loggedIn
    implicit val navSection = "details"

    val id          = ApplicationId.random
    val clientId    = ClientId("clientId")
    val appName     = "an application"
    val createdOn   = LocalDateTime.now(ZoneOffset.UTC)
    val lastAccess  = Some(LocalDateTime.now(ZoneOffset.UTC))
    val grantLength = Period.ofDays(547)
    val deployedTo  = Environment.PRODUCTION

    "viewing an agreed application" should {
      trait Setup {
        val emailAddress      = "email@example.com".toLaxEmail
        val timeStamp         = LocalDateTime.now(ZoneOffset.UTC)
        val expectedTimeStamp = DateTimeFormatter.ofPattern("dd MMMM yyyy").format(timeStamp)
        val version           = "1.0"
        val checkInformation  = CheckInformation(termsOfUseAgreements = List(TermsOfUseAgreement(emailAddress, timeStamp, version)))
        val application       = Application(id, clientId, appName, createdOn, lastAccess, None, grantLength, deployedTo, checkInformation = Some(checkInformation))
        val page: Page        =
          Page(termsOfUseView(ApplicationViewModel(application, hasSubscriptionsFields = false, hasPpnsFields = false), TermsOfUseForm.form, TermsOfUseVersion.latest))
      }

      "set the title and header to 'Terms of use'" in new Setup {
        page.title should startWith("Terms of use")
        page.header.text shouldBe "Terms of use"
      }

      "show a notice stating when the terms of use were agreed to and by whom" in new Setup {
        page.alert.text shouldBe s"Terms of use accepted on $expectedTimeStamp by $emailAddress."
      }

      "render the terms of use" in new Setup {
        page.termsOfUse should not be null
      }

      "not show the agreement form" in new Setup {
        page.agreementForm shouldBe null
      }
    }

    "viewing an unagreed application" should {
      trait Setup {
        val checkInformation = CheckInformation(termsOfUseAgreements = List.empty)
        val application      = Application(id, clientId, appName, createdOn, lastAccess, None, grantLength, deployedTo, checkInformation = Some(checkInformation))
        val page: Page       =
          Page(termsOfUseView(ApplicationViewModel(application, hasSubscriptionsFields = false, hasPpnsFields = false), TermsOfUseForm.form, TermsOfUseVersion.latest))
      }

      "set the title and header to 'Terms of use'" in new Setup {
        page.title should startWith("Agree to our terms of use")
        page.header.text shouldBe "Agree to our terms of use"
      }

      "not show a notice stating when the terms of use were agreed to and by whom" in new Setup {
        page.alert shouldBe null
      }

      "render the terms of use" in new Setup {
        page.termsOfUse should not be null
      }

      "show the agreement form" in new Setup {
        page.agreementForm should not be null
      }
    }
  }
}
