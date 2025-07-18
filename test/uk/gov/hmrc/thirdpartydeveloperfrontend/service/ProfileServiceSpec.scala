/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.thirdpartydeveloperfrontend.service

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future.{failed, successful}

import play.api.http.Status.OK
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}

import uk.gov.hmrc.apiplatform.modules.common.domain.models.{UserId, _}
import uk.gov.hmrc.apiplatform.modules.common.utils.FixedClock
import uk.gov.hmrc.apiplatform.modules.tpd.core.dto.UpdateRequest
import uk.gov.hmrc.apiplatform.modules.tpd.test.data.UserTestData
import uk.gov.hmrc.apiplatform.modules.tpd.test.utils.LocalUserIdTracker
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.DeveloperSessionBuilder
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.ApiPlatformDeskproConnector.{UpdateProfileFailed, UpdateProfileSuccess}
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors._
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.connectors.{ApmConnectorCommandModuleMockModule, ApmConnectorMockModule}
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.AsyncHmrcSpec

class ProfileServiceSpec extends AsyncHmrcSpec
    with LocalUserIdTracker
    with DeveloperSessionBuilder
    with UserTestData {

  trait Setup extends FixedClock with ApmConnectorMockModule with ApmConnectorCommandModuleMockModule {
    implicit val hc: HeaderCarrier = HeaderCarrier()

    val mockDeveloperConnector: ThirdPartyDeveloperConnector = mock[ThirdPartyDeveloperConnector]
    val mockDeskproConnector: ApiPlatformDeskproConnector    = mock[ApiPlatformDeskproConnector]

    val email     = LaxEmailAddress("jed@fastshow.com")
    val firstName = "Jed"
    val lastName  = "Spence"
    val name      = "Jed Spence"

    val profileService = new ProfileService(
      mockDeskproConnector,
      mockDeveloperConnector,
      clock
    )
  }

  "Update profile name" should {
    "call the TPD and Deskpro connectors correctly" in new Setup {
      val userId = UserId.random

      when(mockDeveloperConnector.updateProfile(eqTo(userId), eqTo(UpdateRequest(firstName, lastName)))(*))
        .thenReturn(successful(OK))
      when(mockDeskproConnector.updatePersonName(eqTo(email), eqTo(name), eqTo(hc)))
        .thenReturn(successful(UpdateProfileSuccess))

      val result = await(profileService.updateProfileName(userId, email, firstName, lastName))

      result shouldBe OK

      verify(mockDeveloperConnector, times(1)).updateProfile(eqTo(userId), eqTo(UpdateRequest(firstName, lastName)))(eqTo(hc))
      verify(mockDeskproConnector, times(1)).updatePersonName(eqTo(email), eqTo(name), eqTo(hc))
    }

    "handle error in call to TPD connector correctly" in new Setup {
      val userId = UserId.random

      when(mockDeveloperConnector.updateProfile(eqTo(userId), eqTo(UpdateRequest(firstName, lastName)))(*))
        .thenReturn(failed(UpstreamErrorResponse("auth fail", 401)))
      when(mockDeskproConnector.updatePersonName(eqTo(email), eqTo(name), eqTo(hc)))
        .thenReturn(successful(UpdateProfileSuccess))

      intercept[UpstreamErrorResponse] {
        await(profileService.updateProfileName(userId, email, firstName, lastName))
      }

      verify(mockDeveloperConnector, times(1)).updateProfile(eqTo(userId), eqTo(UpdateRequest(firstName, lastName)))(eqTo(hc))
    }

    "handle error in call to Deskpro connector correctly" in new Setup {
      val userId = UserId.random

      when(mockDeveloperConnector.updateProfile(eqTo(userId), eqTo(UpdateRequest(firstName, lastName)))(*))
        .thenReturn(successful(OK))
      when(mockDeskproConnector.updatePersonName(eqTo(email), eqTo(name), eqTo(hc)))
        .thenReturn(successful(UpdateProfileFailed))

      val result = await(profileService.updateProfileName(userId, email, firstName, lastName))

      result shouldBe OK

      verify(mockDeveloperConnector, times(1)).updateProfile(eqTo(userId), eqTo(UpdateRequest(firstName, lastName)))(eqTo(hc))
      verify(mockDeskproConnector, times(1)).updatePersonName(eqTo(email), eqTo(name), eqTo(hc))
    }
  }
}
