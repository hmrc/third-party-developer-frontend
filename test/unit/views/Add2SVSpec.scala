/*
 * Copyright 2019 HM Revenue & Customs
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

package unit.views

import config.ApplicationConfig
import domain.Developer
import model.MfaMandateDetails
import org.joda.time.LocalDate
import org.mockito.BDDMockito.given
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.OneServerPerSuite
import play.api.i18n.Messages.Implicits._
import play.api.test.FakeRequest
import uk.gov.hmrc.play.test.UnitSpec
import utils.CSRFTokenHelper._

class Add2SVSpec extends UnitSpec with OneServerPerSuite with MockitoSugar {

  val loggedInUser = Developer("admin@example.com", "firstName1", "lastName1")
  private implicit val appConfig: ApplicationConfig = mock[ApplicationConfig]

  private def renderPage(mfaMandateDetails: MfaMandateDetails) = {
    val request = FakeRequest().withCSRFToken

    views.html.add2SV.render(mfaMandateDetails, applicationMessages, request, appConfig)
  }

  "MFA Admin warning" should {
    "not be displayed" in {
        val page = renderPage(MfaMandateDetails(showAdminMfaMandatedMessage = false, daysTillAdminMfaMandate = 0))

        page.contentType should include("text/html")
        page.body should not include "days remaining until 2SV will be mandated for Admins"
      }

    "is displayed with plural 'days remaining'" in {
      given(appConfig.dateOfAdminMfaMandate).willReturn(Some(new LocalDate().plusDays(1)))

      val daysRemaining = 10
      val page = renderPage(MfaMandateDetails(showAdminMfaMandatedMessage = true, daysTillAdminMfaMandate = daysRemaining))

      page.contentType should include("text/html")

      page.body should include(s"$daysRemaining days remaining until 2SV will be mandated for Admins.")
    }

    "is displayed with singular 'day remaining'" in {
      given(appConfig.dateOfAdminMfaMandate).willReturn(Some(new LocalDate().plusDays(1)))

      val daysRemaining = 1
      val page = renderPage(MfaMandateDetails(showAdminMfaMandatedMessage = true, daysTillAdminMfaMandate = daysRemaining))

      page.contentType should include("text/html")

      page.body should include(s"$daysRemaining day remaining until 2SV will be mandated for Admins.")
    }
  }
}