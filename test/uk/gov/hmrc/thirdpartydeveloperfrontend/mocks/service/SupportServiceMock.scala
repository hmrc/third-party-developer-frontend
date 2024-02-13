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

import scala.concurrent.Future.successful

import org.mockito.{ArgumentMatchersSugar, MockitoSugar}

import uk.gov.hmrc.apiplatform.modules.apis.domain.models.{ApiDefinition, ExtendedApiDefinition, ServiceName}
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.SupportService

trait SupportServiceMockModule extends MockitoSugar with ArgumentMatchersSugar {

  trait AbstractSupportServiceMock {
    def aMock: SupportService

    object FetchAllPublicApis {

      def succeeds(apis: List[ApiDefinition]) =
        when(aMock.fetchAllPublicApis()(*)).thenReturn(successful(apis))
    }

    object fetchApiDefinition {

      def succeeds(apiDefinition: ExtendedApiDefinition) =
        when(aMock.fetchApiDefinition(*[ServiceName])(*)).thenReturn(successful(Right(apiDefinition)))
    }
  }

  object SupportServiceMock extends AbstractSupportServiceMock {
    val aMock: SupportService = mock[SupportService]
  }
}
