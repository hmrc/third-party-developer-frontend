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
import java.time.{Period, ZoneOffset}

import org.jsoup.Jsoup
import views.helper.CommonViewSpec
import views.html.TermsOfUseView

import play.api.test.FakeRequest
import play.twirl.api.HtmlFormat.Appendable

import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.{ApplicationState, ApplicationWithCollaboratorsFixtures, CheckInformation, State, TermsOfUseAgreement}
import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress.StringSyntax
import uk.gov.hmrc.apiplatform.modules.common.domain.models.{ApplicationId, ClientId, Environment}
import uk.gov.hmrc.apiplatform.modules.common.utils.FixedClock
import uk.gov.hmrc.apiplatform.modules.tpd.test.builders.UserBuilder
import uk.gov.hmrc.apiplatform.modules.tpd.test.utils.LocalUserIdTracker
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.DeveloperSessionBuilder
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.TermsOfUseVersion
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.controllers.ApplicationViewModel
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithCSRFAddToken

class TermsOfUseSpec extends CommonViewSpec
    with WithCSRFAddToken
    with LocalUserIdTracker
    with DeveloperSessionBuilder
    with UserBuilder
    with ApplicationWithCollaboratorsFixtures
    with FixedClock {

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
    implicit val loggedIn   = buildUser("developer@example.com".toLaxEmail, "Joe", "Bloggs").loggedIn
    implicit val navSection = "details"

    "viewing an agreed application" should {
      trait Setup {
        val emailAddress      = "email@example.com".toLaxEmail
        val expectedTimeStamp = DateTimeFormatter.ofPattern("dd MMMM yyyy").withZone(ZoneOffset.UTC).format(instant)
        val version           = "1.0"
        val checkInformation  = CheckInformation(termsOfUseAgreements = List(TermsOfUseAgreement(emailAddress, instant, version)))
        val application       = standardApp.modify(_.copy(checkInformation = Some(checkInformation)))
        val page: Page        =
          Page(termsOfUseView(ApplicationViewModel(application, hasSubscriptionsFields = false, hasPpnsFields = false), TermsOfUseVersion.latest))
      }

      "set the title and header to 'Terms of use'" in new Setup {
        page.title should startWith("Terms of use")
        page.header.text shouldBe "Terms of use"
      }

      "show a notice stating when the terms of use were agreed to and by whom" in new Setup {
        page.alert.text shouldBe s"Terms of use accepted on $expectedTimeStamp by ${emailAddress.text}."
      }

      "render the terms of use" in new Setup {
        page.termsOfUse should not be null
      }

      "not show the agreement form" in new Setup {
        page.agreementForm shouldBe null
      }
    }
  }
}
