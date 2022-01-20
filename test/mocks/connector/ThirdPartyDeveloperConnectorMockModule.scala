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

package mocks.connector

import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.ThirdPartyDeveloperConnector
import domain.models.developers.{Developer, UserId}
import org.mockito.{ArgumentMatchersSugar, MockitoSugar}

import scala.concurrent.Future.successful

trait ThirdPartyDeveloperConnectorMockModule extends MockitoSugar with ArgumentMatchersSugar {
  

  object TPDMock {
    val aMock = mock[ThirdPartyDeveloperConnector]

    object FindUserId {
      def thenReturn(email: String)(userId: UserId) =
        when(aMock.findUserId(eqTo(email))(*)).thenReturn(successful(Some(ThirdPartyDeveloperConnector.CoreUserDetails(email, userId))))
    }

    object FetchDeveloper {
      def thenReturn(userId: UserId)(developer: Developer) =
        when(aMock.fetchDeveloper(eqTo(userId))(*)).thenReturn(successful(Some(developer)))
    }
  }
}
