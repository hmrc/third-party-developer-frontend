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

package uk.gov.hmrc.apiplatform.modules.submissions.services.mocks

import scala.concurrent.Future.successful

import org.mockito.quality.Strictness
import org.mockito.{ArgumentMatchersSugar, MockitoSugar}

import uk.gov.hmrc.apiplatform.modules.applications.domain.models.ApplicationId
import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress
import uk.gov.hmrc.apiplatform.modules.submissions.domain.models._
import uk.gov.hmrc.apiplatform.modules.submissions.services.SubmissionService

trait SubmissionServiceMockModule extends MockitoSugar with ArgumentMatchersSugar {

  protected trait BaseSubmissionServiceMock {
    def aMock: SubmissionService

    object FetchLatestSubmission {

      def thenReturns(out: Submission) =
        when(aMock.fetchLatestSubmission(*[ApplicationId])(*)).thenReturn(successful(Some(out)))

      def thenReturnsNone() = {
        when(aMock.fetchLatestSubmission(*[ApplicationId])(*)).thenReturn(successful(None))
      }
    }

    object FetchLatestExtendedSubmission {

      def thenReturns(out: ExtendedSubmission) =
        when(aMock.fetchLatestExtendedSubmission(*[ApplicationId])(*)).thenReturn(successful(Some(out)))

      def thenReturnsNone() = {
        when(aMock.fetchLatestExtendedSubmission(*[ApplicationId])(*)).thenReturn(successful(None))
      }
    }

    object Fetch {

      def thenReturns(out: ExtendedSubmission) = {
        when(aMock.fetch(*[Submission.Id])(*)).thenReturn(successful(Some(out)))
      }

      def thenReturnsNone() = {
        when(aMock.fetch(*[Submission.Id])(*)).thenReturn(successful(None))
      }
    }

    object RecordAnswer {

      def thenReturns(out: ExtendedSubmission) = {
        when(aMock.recordAnswer(*[Submission.Id], *[Question.Id], *)(*)).thenReturn(successful(Right(out)))
      }

      def thenReturnsNone() = {
        when(aMock.recordAnswer(*[Submission.Id], *[Question.Id], *)(*)).thenReturn(successful(Left("Failed to record answer for submission")))
      }
    }

    object ConfirmSetupComplete {

      def thenReturnSuccessFor(applicationId: ApplicationId, userEmailAddress: LaxEmailAddress) = {
        when(aMock.confirmSetupComplete(eqTo(applicationId), eqTo(userEmailAddress))(*)).thenReturn(successful(Right(())))
      }

      def thenReturnFailure() = {
        when(aMock.confirmSetupComplete(*[ApplicationId], *[LaxEmailAddress])(*)).thenReturn(successful(Left("nope")))
      }
    }
  }

  object SubmissionServiceMock extends BaseSubmissionServiceMock {
    val aMock = mock[SubmissionService](withSettings.strictness(Strictness.LENIENT))
  }
}
