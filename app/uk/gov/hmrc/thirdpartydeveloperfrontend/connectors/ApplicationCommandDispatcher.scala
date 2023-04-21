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

import scala.concurrent.ExecutionContext

import com.google.inject.{Inject, Singleton}

import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpResponse, InternalServerException}

import uk.gov.hmrc.apiplatform.modules.applications.domain.models.ApplicationId
import uk.gov.hmrc.apiplatform.modules.commands.applications.domain.models.{CommandHandlerTypes, _}
import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress
import uk.gov.hmrc.apiplatform.modules.common.services.ApplicationLogger

@Singleton
class ApplicationCommandConnector @Inject() (
    val http: HttpClient,
    val config: ApmConnector.Config
  )(implicit val ec: ExecutionContext
  ) extends CommandHandlerTypes[DispatchSuccessResult]
    with ApplicationLogger {

  def dispatch(
      applicationId: ApplicationId,
      command: ApplicationCommand,
      adminsToEmail: Set[LaxEmailAddress]
    )(implicit hc: HeaderCarrier
    ): Result = {

    import uk.gov.hmrc.apiplatform.modules.common.domain.services.NonEmptyListFormatters._
    import play.api.libs.json._
    import uk.gov.hmrc.http.HttpReads.Implicits._
    import play.api.http.Status._

    val serviceBaseUrl = config.serviceBaseUrl

    def baseApplicationUrl(applicationId: ApplicationId) = s"$serviceBaseUrl/applications/${applicationId.value.toString()}"

    def parseWithLogAndThrow[T](input: String)(implicit reads: Reads[T]): T = {
      Json.parse(input).validate[T] match {
        case JsSuccess(t, _) => t
        case JsError(err)    =>
          logger.error(s"Failed to parse >>$input<< due to errors $err")
          throw new InternalServerException("Failed parsing response to dispatch")
      }
    }
    val url                                                                 = s"${baseApplicationUrl(applicationId)}/dispatch"
    val request                                                             = DispatchRequest(command, adminsToEmail)
    val extraHeaders                                                        = Seq.empty[(String, String)]
    import cats.syntax.either._

    http.PATCH[DispatchRequest, HttpResponse](url, request, extraHeaders)
      .map(response =>
        response.status match {
          case OK          => parseWithLogAndThrow[DispatchSuccessResult](response.body).asRight[Failures]
          case BAD_REQUEST => logger.error("BAD REQUEST: "+response.body); parseWithLogAndThrow[Failures](response.body).asLeft[DispatchSuccessResult]
          case status      =>
            logger.error(s"Dispatch failed with status code: $status")
            throw new InternalServerException(s"Failed calling dispatch $status")
        }
      )
  }
}
