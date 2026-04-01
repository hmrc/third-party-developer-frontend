/*
 * Copyright 2026 HM Revenue & Customs
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

import scala.concurrent.Future

import org.mockito.{ArgumentMatchersSugar, MockitoSugar}

import uk.gov.hmrc.apiplatform.modules.common.domain.models.UserId
import uk.gov.hmrc.apiplatform.modules.organisations.submissions.domain.models.{OrganisationAllowList, Submission}
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.OrganisationService

trait OrganisationServiceMock extends MockitoSugar with ArgumentMatchersSugar {

  protected trait BaseOrganisationServiceMock {
    def aMock: OrganisationService

    object FetchOrganisationAllowList {
      def thenReturn(allowList: OrganisationAllowList) = when(aMock.fetchOrganisationAllowList(*[UserId])(*)).thenReturn(Future.successful(Some(allowList)))

      def thenReturnNone() = when(aMock.fetchOrganisationAllowList(*[UserId])(*)).thenReturn(Future.successful(None))
    }

    object FetchLatestSubmissionByUserId {
      def thenReturn(submission: Submission) = when(aMock.fetchLatestSubmissionByUserId(*[UserId])(*)).thenReturn(Future.successful(Some(submission)))

      def thenReturnNone() = when(aMock.fetchLatestSubmissionByUserId(*[UserId])(*)).thenReturn(Future.successful(None))
    }
  }

  object OrganisationServiceMock extends BaseOrganisationServiceMock {
    val aMock = mock[OrganisationService]
  }
}
