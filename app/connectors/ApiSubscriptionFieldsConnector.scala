/*
 * Copyright 2020 HM Revenue & Customs
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

package connectors

import java.net.URLEncoder.encode
import java.util.UUID

import akka.actor.ActorSystem
import akka.pattern.FutureTimeoutSupport
import config.ApplicationConfig
import domain.{APIIdentifier, Environment}
import domain.ApiSubscriptionFields._
import helpers.Retries
import javax.inject.{Inject, Singleton}
import play.api.Logger
import play.api.http.Status.{BAD_REQUEST, NO_CONTENT, OK}
import play.api.libs.json.{Format, Json}
import service.SubscriptionFieldsService.{DefinitionsByApiVersion, SubscriptionFieldsConnector}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, NotFoundException}
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{ExecutionContext, Future}

abstract class AbstractSubscriptionFieldsConnector(implicit ec: ExecutionContext)
    extends SubscriptionFieldsConnector
    with Retries {
  protected val httpClient: HttpClient
  protected val proxiedHttpClient: ProxiedHttpClient
  val environment: Environment
  val serviceBaseUrl: String
  val useProxy: Boolean
  val bearerToken: String
  val apiKey: String

  import SubscriptionFieldsConnector._
  import SubscriptionFieldsConnector.JsonFormatters._

  def http: HttpClient =
    if (useProxy) proxiedHttpClient.withHeaders(bearerToken, apiKey) else httpClient

  def fetchFieldsValuesWithPrefetchedDefinitions(
      clientId: String,
      apiIdentifier: APIIdentifier,
      definitionsCache: DefinitionsByApiVersion
  )(implicit hc: HeaderCarrier): Future[Seq[SubscriptionFieldValue]] = {

    def getDefinitions() = Future.successful(definitionsCache.getOrElse(apiIdentifier, Seq.empty))

    internalFetchFieldValues(getDefinitions)(clientId, apiIdentifier)
  }

  def fetchFieldValues(clientId: String, context: String, version: String)(
      implicit hc: HeaderCarrier
  ): Future[Seq[SubscriptionFieldValue]] = {

    def getDefinitions() = fetchFieldDefinitions(context, version)

    internalFetchFieldValues(getDefinitions)(clientId, APIIdentifier(context, version))
  }

  private def internalFetchFieldValues(
      getDefinitions: () => Future[Seq[SubscriptionFieldDefinition]]
  )(clientId: String, apiIdentifier: APIIdentifier)(
      implicit hc: HeaderCarrier
  ): Future[Seq[SubscriptionFieldValue]] = {

    def joinFieldValuesToDefinitions(
        defs: Seq[SubscriptionFieldDefinition],
        fieldValues: Fields
    ): Seq[SubscriptionFieldValue] = {
      defs.map(field => SubscriptionFieldValue(field, fieldValues.getOrElse(field.name, "")))
    }

    def ifDefinitionsGetValues(
        definitions: Seq[SubscriptionFieldDefinition]
    ): Future[Option[ApplicationApiFieldValues]] = {
      if (definitions.isEmpty) {
        Future.successful(None)
      } else {
        fetchApplicationApiValues(clientId, apiIdentifier.context, apiIdentifier.version)
      }
    }

    for {
      definitions: Seq[SubscriptionFieldDefinition] <- getDefinitions()
      subscriptionFields <- ifDefinitionsGetValues(definitions)
      fieldValues = subscriptionFields.fold(Fields.empty)(_.fields)
    } yield joinFieldValuesToDefinitions(definitions, fieldValues)
  }

  def fetchFieldDefinitions(apiContext: String, apiVersion: String)(
      implicit hc: HeaderCarrier
  ): Future[Seq[SubscriptionFieldDefinition]] = {
    val url = urlSubscriptionFieldDefinition(apiContext, apiVersion)
    Logger.debug(s"fetchFieldDefinitions() - About to call $url in ${environment.toString}")
    retry {
      http.GET[ApiFieldDefinitions](url).map(response => response.fieldDefinitions.map(toDomain))
    } recover recovery(Seq.empty)
  }

  def fetchAllFieldDefinitions()(implicit hc: HeaderCarrier): Future[DefinitionsByApiVersion] = {
    val url = s"$serviceBaseUrl/definition"
    retry {
      for {
        response <- http.GET[AllApiFieldDefinitions](url)
      } yield toDomain(response)

    } recover recovery(DefinitionsByApiVersion.empty)
  }

  def saveFieldValues(clientId: String, apiContext: String, apiVersion: String, fields: Fields)
                     (implicit hc: HeaderCarrier): Future[SubscriptionFieldsPutResponse] = {
    val url = urlSubscriptionFieldValues(clientId, apiContext, apiVersion)

    http.PUT[SubscriptionFieldsPutRequest, HttpResponse](url, SubscriptionFieldsPutRequest(clientId, apiContext, apiVersion, fields)).map { response =>
      response.status match {
        case BAD_REQUEST => SubscriptionFieldsPutFailureResponse(Map.empty) // TODO : Parse the body
        case OK => SubscriptionFieldsPutSuccessResponse // TODO: The subs API returns either 200 or 201
      }
    }
  }

  def deleteFieldValues(clientId: String, apiContext: String, apiVersion: String)(
      implicit hc: HeaderCarrier
  ): Future[FieldsDeleteResult] = {
    val url = urlSubscriptionFieldValues(clientId, apiContext, apiVersion)
    http.DELETE[HttpResponse](url).map { response =>
      response.status match {
        case NO_CONTENT => FieldsDeleteSuccessResult
        case _          => FieldsDeleteFailureResult
      }
    } recover {
      case _: NotFoundException => FieldsDeleteSuccessResult
      case _                    => FieldsDeleteFailureResult
    }
  }

  private def fetchApplicationApiValues(clientId: String, apiContext: String, apiVersion: String)(
      implicit hc: HeaderCarrier
  ): Future[Option[ApplicationApiFieldValues]] = {
    val url = urlSubscriptionFieldValues(clientId, apiContext, apiVersion)
    retry {
      http.GET[ApplicationApiFieldValues](url).map(Some(_))
    } recover recovery(None)
  }

  private def urlEncode(str: String, encoding: String = "UTF-8") = encode(str, encoding)

  private def urlSubscriptionFieldValues(clientId: String, apiContext: String, apiVersion: String) =
    s"$serviceBaseUrl/field/application/${urlEncode(clientId)}/context/${urlEncode(apiContext)}/version/${urlEncode(apiVersion)}"

  private def urlSubscriptionFieldDefinition(apiContext: String, apiVersion: String) =
    s"$serviceBaseUrl/definition/context/${urlEncode(apiContext)}/version/${urlEncode(apiVersion)}"

  private def recovery[T](value: T): PartialFunction[Throwable, T] = {
    case _: NotFoundException => value
  }
}

object SubscriptionFieldsConnector {

  def toDomain(f: FieldDefinition): SubscriptionFieldDefinition = {
    SubscriptionFieldDefinition(
      name = f.name,
      description = f.description,
      shortDescription = f.shortDescription,
      `type` = f.`type`,
      hint = f.hint
    )
  }

  def toDomain(fs: AllApiFieldDefinitions): DefinitionsByApiVersion = {
    fs.apis
      .map(fd => APIIdentifier(fd.apiContext, fd.apiVersion) -> fd.fieldDefinitions.map(toDomain))
      .toMap
  }

  private[connectors] case class ApplicationApiFieldValues(
      clientId: String,
      apiContext: String,
      apiVersion: String,
      fieldsId: UUID,
      fields: Map[String, String]
  )

  private[connectors] case class FieldDefinition(
      name: String,
      description: String,
      shortDescription: String,
      hint: String,
      `type`: String
  )

  private[connectors] case class ApiFieldDefinitions(
      apiContext: String,
      apiVersion: String,
      fieldDefinitions: List[FieldDefinition]
  )

  private[connectors] case class AllApiFieldDefinitions(apis: Seq[ApiFieldDefinitions])

  object JsonFormatters {
    implicit val format: Format[ApplicationApiFieldValues] = Json.format[ApplicationApiFieldValues]
    implicit val formatFieldDefinition: Format[FieldDefinition] = Json.format[FieldDefinition]
    implicit val formatApiFieldDefinitionsResponse: Format[ApiFieldDefinitions] =
      Json.format[ApiFieldDefinitions]
    implicit val formatAllApiFieldDefinitionsResponse: Format[AllApiFieldDefinitions] =
      Json.format[AllApiFieldDefinitions]
  }
}

@Singleton
class SandboxSubscriptionFieldsConnector @Inject() (
    val appConfig: ApplicationConfig,
    val httpClient: HttpClient,
    val proxiedHttpClient: ProxiedHttpClient,
    val actorSystem: ActorSystem,
    val futureTimeout: FutureTimeoutSupport
)(implicit val ec: ExecutionContext)
    extends AbstractSubscriptionFieldsConnector {

  val environment: Environment = Environment.SANDBOX
  val serviceBaseUrl: String = appConfig.apiSubscriptionFieldsSandboxUrl
  val useProxy: Boolean = appConfig.apiSubscriptionFieldsSandboxUseProxy
  val bearerToken: String = appConfig.apiSubscriptionFieldsSandboxBearerToken
  val apiKey: String = appConfig.apiSubscriptionFieldsSandboxApiKey
}

@Singleton
class ProductionSubscriptionFieldsConnector @Inject() (
    val appConfig: ApplicationConfig,
    val httpClient: HttpClient,
    val proxiedHttpClient: ProxiedHttpClient,
    val actorSystem: ActorSystem,
    val futureTimeout: FutureTimeoutSupport
)(implicit val ec: ExecutionContext)
    extends AbstractSubscriptionFieldsConnector {

  val environment: Environment = Environment.PRODUCTION
  val serviceBaseUrl: String = appConfig.apiSubscriptionFieldsProductionUrl
  val useProxy: Boolean = appConfig.apiSubscriptionFieldsProductionUseProxy
  val bearerToken: String = appConfig.apiSubscriptionFieldsProductionBearerToken
  val apiKey: String = appConfig.apiSubscriptionFieldsProductionApiKey
}
