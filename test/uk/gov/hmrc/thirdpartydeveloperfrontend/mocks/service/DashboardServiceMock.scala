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

import uk.gov.hmrc.apiplatform.modules.common.domain.models.UserId
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.controllers.ApplicationSummary
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.DashboardService

trait DashboardServiceMockModule extends MockitoSugar with ArgumentMatchersSugar {

  protected trait BaseDashboardServiceMock {
    def aMock: DashboardService

    object FetchApplicationList {

      def thenReturn(apps: Seq[ApplicationSummary]) =
        when(aMock.fetchApplicationList(*[UserId])(*)).thenAnswer(successful(apps))
    }
  }

  object DashboardServiceMock extends BaseDashboardServiceMock {
    val aMock = mock[DashboardService]
  }
}
