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

package uk.gov.hmrc.apiplatform.modules.submissions.connectors

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Success, Try}

import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpResponse, UpstreamErrorResponse}
import uk.gov.hmrc.play.http.metrics.common.API


import uk.gov.hmrc.apiplatform.modules.submissions.domain.models._
import uk.gov.hmrc.apiplatform.modules.submissions.domain.services._
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.ConnectorMetrics
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._
import uk.gov.hmrc.apiplatform.modules.common.domain.models._

object ThirdPartyApplicationSubmissionsConnector {
  case class Config(serviceBaseUrl: String, apiKey: String)

  case class OutboundRecordAnswersRequest(answers: List[String])
  implicit val writesOutboundRecordAnswersRequest = Json.writes[OutboundRecordAnswersRequest]

  case class ApprovalsRequest(requestedByName: String, requestedByEmailAddress: LaxEmailAddress)
  implicit val writesApprovalsRequest = Json.writes[ApprovalsRequest]

  case class ResponsibleIndividualVerificationRequest(code: String)
  implicit val writesResponsibleIndividualVerificationRequest = Json.writes[ResponsibleIndividualVerificationRequest]

  case class ConfirmSetupCompleteRequest(requesterEmailAddress: LaxEmailAddress)
  implicit val writesConfirmSetupCompleteRequest = Json.writes[ConfirmSetupCompleteRequest]

  case class CreateSubmissionRequest(requestedBy: LaxEmailAddress)
  implicit val readsCreateSubmissionRequest = Json.writes[CreateSubmissionRequest]
}

@Singleton
class ThirdPartyApplicationSubmissionsConnector @Inject() (
    val http: HttpClient,
    val config: ThirdPartyApplicationSubmissionsConnector.Config,
    val metrics: ConnectorMetrics
  )(implicit val ec: ExecutionContext
  ) extends SubmissionsFrontendJsonFormatters {

  import ThirdPartyApplicationSubmissionsConnector._
  import config._

  val api = API("third-party-application-submissions")

  val environment = Environment.PRODUCTION

  def recordAnswer(submissionId: Submission.Id, questionId: Question.Id, rawAnswers: List[String])(implicit hc: HeaderCarrier): Future[Either[String, ExtendedSubmission]] = {
    import cats.implicits._
    val failed = (err: UpstreamErrorResponse) => s"Failed to record answer for submission $submissionId and question ${questionId.value}"

    metrics.record(api) {
      http.POST[OutboundRecordAnswersRequest, Either[UpstreamErrorResponse, ExtendedSubmission]](
        s"$serviceBaseUrl/submissions/$submissionId/question/${questionId.value}",
        OutboundRecordAnswersRequest(rawAnswers)
      )
        .map(_.leftMap(failed))
    }
  }

  def fetchLatestSubmission(applicationId: ApplicationId)(implicit hc: HeaderCarrier): Future[Option[Submission]] = {
    metrics.record(api) {
      http.GET[Option[Submission]](s"$serviceBaseUrl/submissions/application/${applicationId}")
    }
  }

  def createSubmission(applicationId: ApplicationId, requestedBy: LaxEmailAddress)(implicit hc: HeaderCarrier): Future[Option[Submission]] = {
    metrics.record(api) {
      http.POST[CreateSubmissionRequest, Option[Submission]](s"$serviceBaseUrl/submissions/application/${applicationId}", CreateSubmissionRequest(requestedBy))
    }
  }

  def fetchLatestExtendedSubmission(applicationId: ApplicationId)(implicit hc: HeaderCarrier): Future[Option[ExtendedSubmission]] = {
    metrics.record(api) {
      http.GET[Option[ExtendedSubmission]](s"$serviceBaseUrl/submissions/application/${applicationId}/extended")
    }
  }

  def fetchSubmission(id: Submission.Id)(implicit hc: HeaderCarrier): Future[Option[ExtendedSubmission]] = {
    metrics.record(api) {
      http.GET[Option[ExtendedSubmission]](s"$serviceBaseUrl/submissions/${id.value}")
    }
  }

  def fetchResponsibleIndividualVerification(code: String)(implicit hc: HeaderCarrier): Future[Option[ResponsibleIndividualVerification]] =
    metrics.record(api) {
      http.GET[Option[ResponsibleIndividualVerification]](s"$serviceBaseUrl/approvals/responsible-individual-verification/${code}")
    }

  def requestApproval(
      applicationId: ApplicationId,
      requestedByName: String,
      requestedByEmailAddress: LaxEmailAddress
    )(implicit hc: HeaderCarrier
    ): Future[Either[ErrorDetails, Application]] =
    metrics.record(api) {
      import play.api.http.Status._

      val url = s"$serviceBaseUrl/approvals/application/${applicationId}/request"

      http.POST[ApprovalsRequest, HttpResponse](url, ApprovalsRequest(requestedByName, requestedByEmailAddress)).map { response =>
        val jsValue: Try[JsValue] = Try(response.json)
        lazy val badResponse      = new RuntimeException("Something went wrong in the response")

        (response.status, jsValue) match {
          case (OK, Success(value))                  => Right(value.asOpt[Application].getOrElse(throw badResponse))
          case (PRECONDITION_FAILED, Success(value)) => Left(value.asOpt[ErrorDetails].getOrElse(throw badResponse))
          case (CONFLICT, Success(value))            => Left(value.asOpt[ErrorDetails].getOrElse(throw badResponse))
          case (_, _)                                => throw badResponse
        }
      }
    }

  def confirmSetupComplete(applicationId: ApplicationId, userEmailAddress: LaxEmailAddress)(implicit hc: HeaderCarrier): Future[Either[String, Unit]] = metrics.record(api) {
    import cats.implicits._

    val url    = s"$serviceBaseUrl/application/${applicationId}/confirm-setup-complete"
    val failed = (err: UpstreamErrorResponse) => s"Failed to confirm setup complete for application ${applicationId}"

    http.POST[ConfirmSetupCompleteRequest, Either[UpstreamErrorResponse, Unit]](url, ConfirmSetupCompleteRequest(userEmailAddress)).map(_.leftMap(failed))
  }
}
