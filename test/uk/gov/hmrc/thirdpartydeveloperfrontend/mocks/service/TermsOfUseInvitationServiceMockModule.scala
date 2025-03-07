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

package uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.service

import java.time.Instant
import scala.concurrent.Future.successful

import org.mockito.{ArgumentMatchersSugar, MockitoSugar}

import uk.gov.hmrc.apiplatform.modules.common.domain.models.ApplicationId
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors.TermsOfUseInvitation
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors.TermsOfUseInvitationState.EMAIL_SENT
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.TermsOfUseInvitationService

trait TermsOfUseInvitationServiceMockModule extends MockitoSugar with ArgumentMatchersSugar {

  protected trait BaseTermsOfUseInvitationServiceMock {
    def aMock: TermsOfUseInvitationService

    object FetchTermsOfUseInvitation {

      def thenReturn() =
        when(aMock.fetchTermsOfUseInvitation(*[ApplicationId])(*)).thenAnswer(successful(Some(TermsOfUseInvitation(
          ApplicationId.random,
          Instant.now,
          Instant.now,
          Instant.now,
          None,
          EMAIL_SENT
        ))))

      def thenReturnNone() = when(aMock.fetchTermsOfUseInvitation(*[ApplicationId])(*)).thenAnswer(successful(None))
    }
  }

  object TermsOfUseInvitationServiceMock extends BaseTermsOfUseInvitationServiceMock {
    val aMock = mock[TermsOfUseInvitationService]
  }
}
