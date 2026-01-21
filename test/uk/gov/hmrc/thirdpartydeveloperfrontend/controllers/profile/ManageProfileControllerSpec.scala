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

package uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.profile

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import org.jsoup.Jsoup
import views.html.manageprofile._

import play.api.http.Status.OK
import play.api.test.Helpers._

import uk.gov.hmrc.apiplatform.modules.common.domain.models.{OrganisationId, UserId}
import uk.gov.hmrc.apiplatform.modules.common.utils.FixedClock
import uk.gov.hmrc.apiplatform.modules.organisations.domain.models.{Organisation, OrganisationName}
import uk.gov.hmrc.apiplatform.modules.tpd.core.domain.models.User
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ErrorHandler
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.ThirdPartyDeveloperConnector
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.BaseControllerSpec
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.service.ApplicationServiceMock
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.{AuditService, DashboardService, ProfileService}
import uk.gov.hmrc.thirdpartydeveloperfrontend.testdata.CommonSessionData
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.ViewHelpers._
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithCSRFAddToken

class ManageProfileControllerSpec extends BaseControllerSpec with WithCSRFAddToken {

  trait Setup extends ApplicationServiceMock with FixedClock {
    val profileDetailsView   = app.injector.instanceOf[ProfileDetailsView]
    val mockDashboardService = mock[DashboardService]

    val underTest = new ManageProfileController(
      applicationServiceMock,
      mock[ProfileService],
      mock[AuditService],
      sessionServiceMock,
      mock[ThirdPartyDeveloperConnector],
      mock[ErrorHandler],
      mcc,
      cookieSigner,
      mockDashboardService,
      profileDetailsView
    )

    val loggedInDeveloper: User = devUser
    val sessionId               = devSession.sessionId
    val organisations           = List(Organisation(OrganisationId.random, OrganisationName("Pete's organisation"), Organisation.OrganisationType.UkLimitedCompany, instant, Set.empty))
  }

  "profileDetails" should {
    "display users profile details" in new Setup {
      when(mockDashboardService.fetchOrganisationsByUserId(*[UserId])(*))
        .thenReturn(Future.successful(organisations))

      val result = addToken(underTest.profileDetails())(loggedInDevRequest)

      status(result) shouldBe OK
      val doc = Jsoup.parse(contentAsString(result))
      withClue("name")(elementIdentifiedByIdContainsText(doc, "name", CommonSessionData.dev.developer.displayedName) shouldBe true)
      withClue("email")(elementIdentifiedByIdContainsText(doc, "email", CommonSessionData.dev.developer.email.text) shouldBe true)
    }
  }
}
