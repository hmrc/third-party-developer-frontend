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

package views.checkpages

import config.ApplicationConfig
import controllers.TermsOfUseForm
import domain._
import model.ApplicationViewModel
import org.jsoup.Jsoup
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.{OneAppPerSuite, PlaySpec}
import play.api.i18n.Messages.Implicits._
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.{AnyContentAsEmpty, Call}
import play.api.test.FakeRequest
import uk.gov.hmrc.time.DateTimeUtils
import views.html.checkpages.termsOfUse

class TermsOfUseSpec extends PlaySpec with OneAppPerSuite with MockitoSugar {

  override def fakeApplication(): play.api.Application =
    GuiceApplicationBuilder()
      .configure(("metrics.jvm", false))
      .build()

  val appConfig: ApplicationConfig = mock[ApplicationConfig]

  "Terms of use view" must {
    val thirdPartyApplication =
      Application(
        "APPLICATION_ID",
        "CLIENT_ID",
        "APPLICATION NAME",
        DateTimeUtils.now,
        DateTimeUtils.now,
        None,
        Environment.PRODUCTION,
        Some("APPLICATION DESCRIPTION"),
        Set(Collaborator("sample@example.com", Role.ADMINISTRATOR), Collaborator("someone@example.com", Role.DEVELOPER)),
        Standard(),
        ApplicationState(State.TESTING, None, None, DateTimeUtils.now)
      )

    "show terms of use agreement page that requires terms of use to be agreed" in {
      implicit val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest().withCSRFToken

      val checkInformation = CheckInformation()

      val termsOfUseForm = TermsOfUseForm.fromCheckInformation(checkInformation)
      val developer = utils.DeveloperSession("email@example.com", "First Name", "Last Name", None, loggedInState = LoggedInState.LOGGED_IN)

      val page = termsOfUse.render(
        ApplicationViewModel(thirdPartyApplication,false),
        form = TermsOfUseForm.form.fill(termsOfUseForm),
        submitButtonLabel = "A Label",
        submitAction = mock[Call],
        landingPageRoute = mock[Call],
        request,
        developer,
        applicationMessages,
        appConfig)
      page.contentType must include("text/html")

      val document = Jsoup.parse(page.body)
      document.getElementById("termsOfUseAgreed") mustNot be(null)
      document.getElementById("termsOfUseAgreed").attr("checked") mustNot be("checked")
    }

    "show terms of use agreement page that already has the correct terms of use agreed" in {
      implicit val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest().withCSRFToken

      val appConfigMock = mock[ApplicationConfig]
      val termsOfUseAgreement = TermsOfUseAgreement("email@example.com", DateTimeUtils.now, "1.0")

      val checkInformation = CheckInformation(termsOfUseAgreements = Seq(termsOfUseAgreement))

      val termsOfUseForm = TermsOfUseForm.fromCheckInformation(checkInformation)
      val developer = utils.DeveloperSession("email@example.com", "First Name", "Last Name", None, loggedInState = LoggedInState.LOGGED_IN)

      val page = termsOfUse.render(
        ApplicationViewModel(thirdPartyApplication.copy(checkInformation = Some(checkInformation)), false),
        form = TermsOfUseForm.form.fill(termsOfUseForm),
        submitButtonLabel =  "A Label",
        submitAction = mock[Call],
        landingPageRoute = mock[Call],
        request,
        developer,
        implicitly,
        appConfigMock)
      page.contentType must include("text/html")

      page.body.contains("Terms of use agreed by email@example.com") mustBe true
    }
  }
}
