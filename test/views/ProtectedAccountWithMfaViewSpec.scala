/*
 * Copyright 2022 HM Revenue & Customs
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

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import play.twirl.api.HtmlFormat
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.{DeveloperBuilder, MfaDetailBuilder}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.{Developer, DeveloperSession, LoggedInState, Session}
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.ViewHelpers.{elementExistsById, elementExistsByText, elementExistsContainsText}
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.{LocalUserIdTracker, WithCSRFAddToken}
import views.helper.CommonViewSpec
import views.html.protectaccount.ProtectedAccountWithMfaView

import java.time.LocalDateTime

class ProtectedAccountWithMfaViewSpec extends CommonViewSpec with WithCSRFAddToken with DeveloperBuilder with LocalUserIdTracker with MfaDetailBuilder {
  implicit val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
  val protectedAccountWithMfaView: ProtectedAccountWithMfaView = app.injector.instanceOf[ProtectedAccountWithMfaView]

  "Protected Account with MFA view" should {

    "render the page with user mfa details list" in {
      val createdOnTimestamp: LocalDateTime = LocalDateTime.of(2022, 8, 2, 15, 20)
      val mfaDetails = List(buildAuthenticatorAppMfaDetail("Google Authenticator", createdOn = createdOnTimestamp, verified = true))
      val developer: Developer = buildDeveloper(mfaDetails = mfaDetails)
      val session: Session = Session("sessionId", developer, LoggedInState.LOGGED_IN)
      implicit val developerSession: DeveloperSession = DeveloperSession(session)

      val renderedView: HtmlFormat.Appendable = protectedAccountWithMfaView.apply(mfaDetails)

      val document: Document = Jsoup.parse(renderedView.body)

      elementExistsByText(document, "h1", "Account protection") shouldBe true
      elementExistsByText(document, "p", "This is how you get your access codes.") shouldBe true
      elementExistsById(document, "mfaType-0") shouldBe true
      elementExistsContainsText(document, "td", "Authenticator App") shouldBe true
      elementExistsById(document, "nameAndCreatedOn-0") shouldBe true
      elementExistsContainsText(document, "td", "Google Authenticator") shouldBe true
      elementExistsContainsText(document, "td", "Added 02 August 2022 15:20") shouldBe true
      elementExistsById(document, "removeMfaLink-0") shouldBe true
      elementExistsContainsText(document, "a", "Remove") shouldBe true
    }

    "render the page without the name when name equals AUTHENTICATOR_APP" in {
      val createdOnTimestamp: LocalDateTime = LocalDateTime.of(2022, 8, 2, 15, 20)
      val mfaDetails = List(buildAuthenticatorAppMfaDetail("AUTHENTICATOR_APP", createdOn = createdOnTimestamp, verified = true))
      val developer: Developer = buildDeveloper(mfaDetails = mfaDetails)
      val session: Session = Session("sessionId", developer, LoggedInState.LOGGED_IN)
      implicit val developerSession: DeveloperSession = DeveloperSession(session)

      val renderedView: HtmlFormat.Appendable = protectedAccountWithMfaView.apply(mfaDetails)

      val document: Document = Jsoup.parse(renderedView.body)

      elementExistsContainsText(document, "td", "AUTHENTICATOR_APP") shouldBe false
      elementExistsContainsText(document, "td", "Added 02 August 2022 15:20") shouldBe true
    }

    "render the page without createdOn field when the createdOn date is before 01 August 2022" in {
      val createdOnTimestamp: LocalDateTime = LocalDateTime.of(2022, 7, 2, 15, 20)
      val mfaDetails = List(buildAuthenticatorAppMfaDetail("Google Authenticator", createdOn = createdOnTimestamp, verified = true))
      val developer: Developer = buildDeveloper(mfaDetails = mfaDetails)
      val session: Session = Session("sessionId", developer, LoggedInState.LOGGED_IN)
      implicit val developerSession: DeveloperSession = DeveloperSession(session)

      val renderedView: HtmlFormat.Appendable = protectedAccountWithMfaView.apply(mfaDetails)

      val document: Document = Jsoup.parse(renderedView.body)

      elementExistsContainsText(document, "td", "Google Authenticator") shouldBe true
      elementExistsContainsText(document, "td", "Added 02 July 2022 15:20") shouldBe false
    }
  }
}