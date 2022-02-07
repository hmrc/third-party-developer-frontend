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

package uk.gov.hmrc.apiplatform.modules.submissions.services.mocks

import org.mockito.{ArgumentMatchersSugar, MockitoSugar}
import uk.gov.hmrc.apiplatform.modules.submissions.services.SubmissionService

import scala.concurrent.Future.successful
import uk.gov.hmrc.apiplatform.modules.submissions.domain.models._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.ApplicationId

trait SubmissionServiceMockModule extends MockitoSugar with ArgumentMatchersSugar {
  
  protected trait BaseSubmissionServiceMock {
    def aMock: SubmissionService

    object FetchLatestSubmission {
      def thenReturns(out: ExtendedSubmission) =
        when(aMock.fetchLatestSubmission(*[ApplicationId])(*)).thenReturn(successful(Some(out)))
        
      def thenReturnsNone() = {
        when(aMock.fetchLatestSubmission(*[ApplicationId])(*)).thenReturn(successful(None))
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
        when(aMock.recordAnswer(*[Submission.Id], *[QuestionId], *)(*)).thenReturn(successful(Right(out)))
      }

      def thenReturnsNone() = {
        when(aMock.recordAnswer(*[Submission.Id], *[QuestionId], *)(*)).thenReturn(successful(Left("Failed to record answer for submission")))
      }
    }
  }

  object SubmissionServiceMock extends BaseSubmissionServiceMock {
    val aMock = mock[SubmissionService](withSettings.lenient())
  }
}
