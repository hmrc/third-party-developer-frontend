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

import play.api.libs.json.{JsValue, Json, Writes}
import play.api.libs.ws.writeableOf_JsValue
import uk.gov.hmrc.http.HttpReads.Implicits.*
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps, UpstreamErrorResponse}

import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.ApplicationWithCollaborators
import uk.gov.hmrc.apiplatform.modules.applications.submissions.domain.models.SubmissionId
import uk.gov.hmrc.apiplatform.modules.common.domain.models.*
import uk.gov.hmrc.apiplatform.modules.submissions.domain.models.*
import uk.gov.hmrc.apiplatform.modules.submissions.domain.models.Submission.given
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.{API, ConnectorMetrics}

object ThirdPartyApplicationSubmissionsConnector {
  case class Config(serviceBaseUrl: String, apiKey: String)

  case class OutboundRecordAnswersRequest(answers: List[String])
  given Writes[OutboundRecordAnswersRequest] = Json.writes[OutboundRecordAnswersRequest]

  case class ApprovalsRequest(requestedByName: String, requestedByEmailAddress: LaxEmailAddress)
  given Writes[ApprovalsRequest] = Json.writes[ApprovalsRequest]

  case class ResponsibleIndividualVerificationRequest(code: String)
  given Writes[ResponsibleIndividualVerificationRequest] = Json.writes[ResponsibleIndividualVerificationRequest]

  case class ConfirmSetupCompleteRequest(requesterEmailAddress: LaxEmailAddress)
  given Writes[ConfirmSetupCompleteRequest] = Json.writes[ConfirmSetupCompleteRequest]

  case class CreateSubmissionRequest(requestedBy: LaxEmailAddress)
  given Writes[CreateSubmissionRequest] = Json.writes[CreateSubmissionRequest]
}

@Singleton
class ThirdPartyApplicationSubmissionsConnector @Inject() (
    val http: HttpClientV2,
    val config: ThirdPartyApplicationSubmissionsConnector.Config,
    val metrics: ConnectorMetrics
  )(using val ec: ExecutionContext
  ) {

  import ThirdPartyApplicationSubmissionsConnector._
  import config._

  val api = API("third-party-application-submissions")

  val environment = Environment.Production

  def recordAnswer(submissionId: SubmissionId, questionId: Question.Id, rawAnswers: List[String])(using HeaderCarrier): Future[Either[String, ExtendedSubmission]] = {
    import cats.implicits._
    val failed = (_: UpstreamErrorResponse) => s"Failed to record answer for submission $submissionId and question ${questionId.value}"

    metrics.record(api) {
      http
        .post(url"$serviceBaseUrl/submissions/$submissionId/question/${questionId.value}")
        .withBody(Json.toJson(OutboundRecordAnswersRequest(rawAnswers)))
        .execute[Either[UpstreamErrorResponse, ExtendedSubmission]]
        .map(_.leftMap(failed))
    }
  }

  def fetchLatestSubmission(applicationId: ApplicationId)(using HeaderCarrier): Future[Option[Submission]] = {
    metrics.record(api) {
      http
        .get(url"$serviceBaseUrl/submissions/application/${applicationId}")
        .execute[Option[Submission]]
    }
  }

  def createSubmission(applicationId: ApplicationId, requestedBy: LaxEmailAddress)(using HeaderCarrier): Future[Option[Submission]] = {
    metrics.record(api) {
      http.post(url"$serviceBaseUrl/submissions/application/${applicationId}")
        .withBody(Json.toJson(CreateSubmissionRequest(requestedBy)))
        .execute[Option[Submission]]
    }
  }

  def fetchLatestExtendedSubmission(applicationId: ApplicationId)(using HeaderCarrier): Future[Option[ExtendedSubmission]] = {
    metrics.record(api) {
      http.get(url"$serviceBaseUrl/submissions/application/${applicationId}/extended")
        .execute[Option[ExtendedSubmission]]
    }
  }

  def fetchSubmission(id: SubmissionId)(using HeaderCarrier): Future[Option[ExtendedSubmission]] = {
    metrics.record(api) {
      http.get(url"$serviceBaseUrl/submissions/${id.value}")
        .execute[Option[ExtendedSubmission]]
    }
  }

  def fetchResponsibleIndividualVerification(code: String)(using HeaderCarrier): Future[Option[ResponsibleIndividualVerification]] =
    metrics.record(api) {
      http.get(url"$serviceBaseUrl/approvals/responsible-individual-verification/${code}")
        .execute[Option[ResponsibleIndividualVerification]]
    }

  def requestApproval(
      applicationId: ApplicationId,
      requestedByName: String,
      requestedByEmailAddress: LaxEmailAddress
    )(using HeaderCarrier
    ): Future[Either[ErrorDetails, ApplicationWithCollaborators]] =
    metrics.record(api) {
      import play.api.http.Status._

      val url = url"$serviceBaseUrl/approvals/application/${applicationId}/request"

      http.post(url)
        .withBody(Json.toJson(ApprovalsRequest(requestedByName, requestedByEmailAddress)))
        .execute[HttpResponse]
        .map { response =>
          val jsValue: Try[JsValue] = Try(response.json)
          lazy val badResponse      = new RuntimeException("Something went wrong in the response")

          (response.status, jsValue) match {
            case (OK, Success(value))                  => Right(value.asOpt[ApplicationWithCollaborators].getOrElse(throw badResponse))
            case (PRECONDITION_FAILED, Success(value)) => Left(value.asOpt[ErrorDetails].getOrElse(throw badResponse))
            case (CONFLICT, Success(value))            => Left(value.asOpt[ErrorDetails].getOrElse(throw badResponse))
            case (_, _)                                => throw badResponse
          }
        }
    }

  def confirmSetupComplete(applicationId: ApplicationId, userEmailAddress: LaxEmailAddress)(using HeaderCarrier): Future[Either[String, Unit]] = metrics.record(api) {
    import cats.implicits._

    val url    = url"$serviceBaseUrl/application/${applicationId}/confirm-setup-complete"
    val failed = (_: UpstreamErrorResponse) => s"Failed to confirm setup complete for application ${applicationId}"

    http.post(url)
      .withBody(Json.toJson(ConfirmSetupCompleteRequest(userEmailAddress)))
      .execute[Either[UpstreamErrorResponse, Unit]]
      .map(_.leftMap(failed))
  }
}
