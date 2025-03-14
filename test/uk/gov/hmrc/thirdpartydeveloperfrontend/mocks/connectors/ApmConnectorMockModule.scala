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

import scala.concurrent.Future.{failed, successful}

import org.mockito.{ArgumentMatchersSugar, MockitoSugar}

import uk.gov.hmrc.apiplatform.modules.apis.domain.models._
import uk.gov.hmrc.apiplatform.modules.common.domain.models._
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.ApmConnector

trait ApmConnectorMockModule extends MockitoSugar with ArgumentMatchersSugar {

  object ApmConnectorMock {
    val aMock = mock[ApmConnector]

    object UpliftApplicationV2 {

      def willReturn(newAppId: ApplicationId) =
        when(aMock.upliftApplicationV2(*[ApplicationId], *)(*)).thenReturn(successful(newAppId))

      def willFailWith(exception: Exception) =
        when(aMock.upliftApplicationV2(*[ApplicationId], *)(*)).thenReturn(failed(exception))
    }

    object FetchAllApis {

      def willReturn(apis: List[ApiDefinition]) =
        when(aMock.fetchAllApis(*)(*)).thenReturn(successful(apis))

      def willFailWith(exception: Exception) =
        when(aMock.fetchAllApis(*)(*)).thenReturn(failed(exception))
    }

    object FetchApiDefinitionsVisibleToUser {

      def willReturn(apis: List[ApiDefinition]) =
        when(aMock.fetchApiDefinitionsVisibleToUser(*)(*)).thenReturn(successful(apis))

      def willFailWith(exception: Exception) =
        when(aMock.fetchApiDefinitionsVisibleToUser(*)(*)).thenReturn(failed(exception))
    }

    object FetchExtendedApiDefinition {

      def willReturn(definition: ExtendedApiDefinition) =
        when(aMock.fetchExtendedApiDefinition(*[ServiceName])(*)).thenReturn(successful(Right(definition)))

      def willFailWith(exception: Exception) =
        when(aMock.fetchExtendedApiDefinition(*[ServiceName])(*)).thenReturn(successful(Left(exception)))
    }

    object FetchUpliftableSubscriptions {

      def willReturn(apiIds: Set[ApiIdentifier]) =
        when(aMock.fetchUpliftableSubscriptions(*[ApplicationId])(*)).thenReturn(successful(apiIds))

      def willFailWith(exception: Exception) =
        when(aMock.fetchUpliftableSubscriptions(*)(*)).thenReturn(failed(exception))
    }
  }
}
