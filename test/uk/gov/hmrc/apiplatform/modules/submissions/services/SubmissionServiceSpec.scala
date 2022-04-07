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

package uk.gov.hmrc.apiplatform.modules.submissions.services

import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.AsyncHmrcSpec
import uk.gov.hmrc.apiplatform.modules.submissions.connectors.ThirdPartyApplicationSubmissionsConnector
import scala.concurrent.Future.successful
import uk.gov.hmrc.apiplatform.modules.submissions.SubmissionsTestData
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.apiplatform.modules.submissions.domain.models.Submission
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.ApplicationId
import uk.gov.hmrc.apiplatform.modules.submissions.domain.models.Question

class SubmissionServiceSpec extends AsyncHmrcSpec {
  trait Setup extends SubmissionsTestData {
    implicit val hc = HeaderCarrier()
    
    val mockSubmissionsConnector = mock[ThirdPartyApplicationSubmissionsConnector]

    val underTest = new SubmissionService(
      mockSubmissionsConnector
    )
  }

  "SubmissionService" should {
    "return extended submission for given submission id" in new Setup {
      when(mockSubmissionsConnector.fetchSubmission(*[Submission.Id])(*)).thenReturn(successful(Some(completelyAnswerExtendedSubmission)))

      val result = await(underTest.fetch(completelyAnswerExtendedSubmission.submission.id))
      
      result shouldBe 'defined
      result.get.submission.id shouldBe completelyAnswerExtendedSubmission.submission.id
    }

    "return latest submission for given application id" in new Setup {
      when(mockSubmissionsConnector.fetchLatestSubmission(*[ApplicationId])(*)).thenReturn(successful(Some(aSubmission)))

      val result = await(underTest.fetchLatestSubmission(aSubmission.applicationId))
      
      result shouldBe 'defined
      result.get.id shouldBe aSubmission.id
    }

    "return latest extended submission for given application id" in new Setup {
      when(mockSubmissionsConnector.fetchLatestExtendedSubmission(*[ApplicationId])(*)).thenReturn(successful(Some(completelyAnswerExtendedSubmission)))

      val result = await(underTest.fetchLatestExtendedSubmission(completelyAnswerExtendedSubmission.submission.applicationId))
      
      result shouldBe 'defined
      result.get.submission.id shouldBe completelyAnswerExtendedSubmission.submission.id
    }

    "record answer for given submisson id and question id" in new Setup {
      when(mockSubmissionsConnector.recordAnswer(*[Submission.Id], *[Question.Id], *)(*)).thenReturn(successful(Right(answeringSubmission.withIncompleteProgress())))

      val result = await(underTest.recordAnswer(completelyAnswerExtendedSubmission.submission.id, questionId, List("")))
      
      // result shouldBe 'defined
      result.isRight shouldBe true
    }
  }
}
