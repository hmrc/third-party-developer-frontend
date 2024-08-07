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

package uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.connectors

import scala.concurrent.Future.successful

import org.mockito.{ArgumentMatchersSugar, MockitoSugar}

import uk.gov.hmrc.apiplatform.modules.common.domain.models.{LaxEmailAddress, UserId}
import uk.gov.hmrc.apiplatform.modules.tpd.core.domain.models.User
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.ThirdPartyDeveloperConnector

trait ThirdPartyDeveloperConnectorMockModule extends MockitoSugar with ArgumentMatchersSugar {

  trait AbstractTPDMock {
    def aMock: ThirdPartyDeveloperConnector

    object FindUserId {

      def thenReturn(email: LaxEmailAddress)(userId: UserId) =
        when(aMock.findUserId(eqTo(email))(*)).thenReturn(successful(Some(ThirdPartyDeveloperConnector.CoreUserDetails(email, userId))))
    }

    object FetchDeveloper {

      def thenReturn(userId: UserId)(developer: Option[User]) =
        when(aMock.fetchDeveloper(eqTo(userId))(*)).thenReturn(successful(developer))
    }

    object FetchByEmails {

      def returnsEmptySeq() =
        when(aMock.fetchByEmails(*)(*)).thenReturn(successful(Seq.empty))

      def returnsSuccessFor(in: Set[LaxEmailAddress])(out: Seq[User]) =
        when(aMock.fetchByEmails(eqTo(in))(*)).thenReturn(successful(out))
    }

    object GetOrCreateUser {
      def succeeds() = succeedsWith(UserId.random)

      def succeedsWith(userId: UserId) =
        when(aMock.getOrCreateUserId(*[LaxEmailAddress])(*)).thenReturn(successful(userId))
    }
  }

  object TPDMock extends AbstractTPDMock {
    val aMock = mock[ThirdPartyDeveloperConnector]
  }
}
