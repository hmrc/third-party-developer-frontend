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

package uk.gov.hmrc.thirdpartydeveloperfrontend.connectors

import scala.concurrent.{ExecutionContext, Future}

import com.google.inject.{Inject, Singleton}

import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, InternalServerException, StringContextOps}

import uk.gov.hmrc.apiplatform.modules.commands.applications.domain.models.{CommandHandlerTypes, _}
import uk.gov.hmrc.apiplatform.modules.common.domain.models.{LaxEmailAddress, _}
import uk.gov.hmrc.apiplatform.modules.common.services.ApplicationLogger
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.ApplicationUpdateSuccessful

@Singleton
class ApplicationCommandConnector @Inject() (
    val http: HttpClientV2,
    val config: ApmConnector.Config
  )(implicit val ec: ExecutionContext
  ) extends CommandHandlerTypes[DispatchSuccessResult]
    with ApplicationLogger {

  // TODO - rework code so this is not required
  def dispatchWithThrow(
      applicationId: ApplicationId,
      command: ApplicationCommand,
      adminsToEmail: Set[LaxEmailAddress]
    )(implicit hc: HeaderCarrier
    ): Future[ApplicationUpdateSuccessful] = {
    dispatch(applicationId, command, adminsToEmail).map(_ match {
      case Left(errs) => throw new RuntimeException(CommandFailures.describe(errs.head))
      case Right(_)   => ApplicationUpdateSuccessful
    })
  }

  def dispatch(
      applicationId: ApplicationId,
      command: ApplicationCommand,
      adminsToEmail: Set[LaxEmailAddress]
    )(implicit hc: HeaderCarrier
    ): AppCmdResult = {

    import uk.gov.hmrc.apiplatform.modules.common.domain.services.NonEmptyListFormatters._
    import play.api.libs.json._
    import uk.gov.hmrc.http.HttpReads.Implicits._
    import play.api.http.Status._

    val serviceBaseUrl = config.serviceBaseUrl

    def baseApplicationUrl(applicationId: ApplicationId) = s"$serviceBaseUrl/applications/${applicationId}"

    def parseWithLogAndThrow[T](input: String)(implicit reads: Reads[T]): T = {
      Json.parse(input).validate[T] match {
        case JsSuccess(t, _) => t
        case JsError(err)    =>
          logger.error(s"Failed to parse >>$input<< due to errors $err")
          throw new InternalServerException("Failed parsing response to dispatch")
      }
    }
    import cats.syntax.either._

    http.patch(url"${baseApplicationUrl(applicationId)}/dispatch")
      .withBody(Json.toJson(DispatchRequest(command, adminsToEmail)))
      .execute[HttpResponse]
      .map(response =>
        response.status match {
          case OK          => parseWithLogAndThrow[DispatchSuccessResult](response.body).asRight[Failures]
          case BAD_REQUEST => logger.error("BAD REQUEST: " + response.body); parseWithLogAndThrow[Failures](response.body).asLeft[DispatchSuccessResult]
          case status      =>
            logger.error(s"Dispatch failed with status code: $status")
            throw new InternalServerException(s"Failed calling dispatch $status")
        }
      )
  }
}
