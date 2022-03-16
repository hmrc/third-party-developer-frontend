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

import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.ApplicationId
import scala.concurrent.Future
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.apiplatform.modules.submissions.domain.models._
import uk.gov.hmrc.apiplatform.modules.submissions.connectors.ThirdPartyApplicationSubmissionsConnector

@Singleton
class SubmissionService @Inject() (productionApplicationConnector: ThirdPartyApplicationSubmissionsConnector) {
  def fetchLatestSubmission(appId: ApplicationId)(implicit hc: HeaderCarrier): Future[Option[Submission]] = productionApplicationConnector.fetchLatestSubmission(appId)
  
  def fetchLatestExtendedSubmission(appId: ApplicationId)(implicit hc: HeaderCarrier): Future[Option[ExtendedSubmission]] = productionApplicationConnector.fetchLatestExtendedSubmission(appId)

  def fetch(id: Submission.Id)(implicit hc: HeaderCarrier): Future[Option[ExtendedSubmission]] = productionApplicationConnector.fetchSubmission(id)

  def recordAnswer(submissionId: Submission.Id, questionId: Question.Id, rawAnswers: List[String])(implicit hc: HeaderCarrier): Future[Either[String, ExtendedSubmission]] = productionApplicationConnector.recordAnswer(submissionId, questionId, rawAnswers)
}