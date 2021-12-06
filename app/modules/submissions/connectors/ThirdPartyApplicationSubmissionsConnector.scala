/*
 * Copyright 2021 HM Revenue & Customs
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

package modules.submissions.connectors

import javax.inject.{Inject, Singleton}
import connectors.ConnectorMetrics
import uk.gov.hmrc.http.HttpClient
import scala.concurrent.ExecutionContext
import modules.submissions.domain.services._
import modules.submissions.domain.models._
import uk.gov.hmrc.http.HeaderCarrier
import scala.concurrent.Future
import domain.models.applications._
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.UpstreamErrorResponse
import play.api.libs.json.Json
import uk.gov.hmrc.play.http.metrics.common.API
<<<<<<< HEAD
import scala.util.{Success, Try}
=======
import scala.util.{Failure, Success, Try}
>>>>>>> main
import uk.gov.hmrc.http.HttpResponse
import play.api.libs.json.JsValue

object ThirdPartyApplicationSubmissionsConnector {
  case class Config(serviceBaseUrl: String, apiKey: String)

  case class OutboundRecordAnswersRequest(answers: List[String])
  implicit val writesOutboundRecordAnswersRequest = Json.writes[OutboundRecordAnswersRequest]

  case class ApprovalsRequest(requestedByEmailAddress: String) 
  implicit val writesApprovalsRequest = Json.writes[ApprovalsRequest]
}

@Singleton
class ThirdPartyApplicationSubmissionsConnector @Inject() (
    val http: HttpClient,
    val config: ThirdPartyApplicationSubmissionsConnector.Config,
    val metrics: ConnectorMetrics
)(implicit val ec: ExecutionContext)
    extends SubmissionsJsonFormatters {

  import ThirdPartyApplicationSubmissionsConnector._
  import config._

  val api = API("third-party-application-submissions")

  val environment = Environment.PRODUCTION

  def recordAnswer(submissionId: SubmissionId, questionId: QuestionId, rawAnswers: List[String])(implicit hc: HeaderCarrier): Future[Either[String, ExtendedSubmission]] = {
    import cats.implicits._
    val failed = (err: UpstreamErrorResponse) => s"Failed to record answer for submission ${submissionId.value} and question ${questionId.value}"

    metrics.record(api) {
      http.POST[OutboundRecordAnswersRequest, Either[UpstreamErrorResponse, ExtendedSubmission]](s"$serviceBaseUrl/submissions/${submissionId.value}/question/${questionId.value}", OutboundRecordAnswersRequest(rawAnswers))
      .map(_.leftMap(failed))
    }
  }

  def fetchLatestSubmission(applicationId: ApplicationId)(implicit hc: HeaderCarrier): Future[Option[ExtendedSubmission]] = {
    metrics.record(api) {
      http.GET[Option[ExtendedSubmission]](s"$serviceBaseUrl/submissions/application/${applicationId.value}")
    }
  }
  
  def fetchSubmission(id: SubmissionId)(implicit hc: HeaderCarrier): Future[Option[ExtendedSubmission]] = {
    metrics.record(api) {
      http.GET[Option[ExtendedSubmission]](s"$serviceBaseUrl/submissions/${id.value}")
    }
  }

  
  def requestApproval(applicationId: ApplicationId, requestedByEmailAddress: String)(implicit hc: HeaderCarrier): Future[Either[ErrorDetails, Application]] = metrics.record(api) {
    import play.api.http.Status._
    
    val url = s"$serviceBaseUrl/application/${applicationId.value}/request-approval"
    
    http.POST[ApprovalsRequest, HttpResponse](url, ApprovalsRequest(requestedByEmailAddress)).map { response =>
      val jsValue: Try[JsValue] = Try(response.json)
      lazy val badResponse = new RuntimeException("Something went wrong in the response")

      (response.status, jsValue) match {
        case (OK, Success(value))                   => Right(value.asOpt[Application].getOrElse(throw badResponse))
        case (PRECONDITION_FAILED, Success(value))  => Left(value.asOpt[ErrorDetails].getOrElse(throw badResponse))
        case (CONFLICT, Success(value))             => Left(value.asOpt[ErrorDetails].getOrElse(throw badResponse))
        case (_, _)                                 => throw badResponse
      }
    }
  }
}
