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

import java.net.URLEncoder.encode
import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.pattern.FutureTimeoutSupport

import play.api.http.Status.{BAD_REQUEST, CREATED, NOT_FOUND, NO_CONTENT, OK}
import play.api.libs.json.{JsSuccess, Json}
import uk.gov.hmrc.apiplatformmicroservice.common.utils.EbridgeConfigurator
import uk.gov.hmrc.http.client.{HttpClientV2, RequestBuilder}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps, UpstreamErrorResponse}

import uk.gov.hmrc.apiplatform.modules.applications.subscriptions.domain.models.{FieldName, FieldValue}
import uk.gov.hmrc.apiplatform.modules.common.domain.models._
import uk.gov.hmrc.apiplatform.modules.common.services.ApplicationLogger
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ApplicationConfig
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.SubscriptionFieldsConnectorDomain._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.subscriptions.ApiSubscriptionFields._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.subscriptions._
import uk.gov.hmrc.thirdpartydeveloperfrontend.helpers.Retries
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.SubscriptionFieldsService.{DefinitionsByApiVersion, SubscriptionFieldsConnector}

abstract class AbstractSubscriptionFieldsConnector(implicit ec: ExecutionContext) extends SubscriptionFieldsConnector with Retries with ApplicationLogger {
  val http: HttpClientV2
  val environment: Environment
  val serviceBaseUrl: String

  import SubscriptionFieldsConnectorJsonFormatters._

  def configureEbridgeIfRequired: RequestBuilder => RequestBuilder

  def fetchFieldsValuesWithPrefetchedDefinitions(
      clientId: ClientId,
      apiIdentifier: ApiIdentifier,
      definitionsCache: DefinitionsByApiVersion
    )(implicit hc: HeaderCarrier
    ): Future[Seq[SubscriptionFieldValue]] = {

    def getDefinitions() = Future.successful(definitionsCache.getOrElse(apiIdentifier, Seq.empty))

    internalFetchFieldValues(() => getDefinitions())(clientId, apiIdentifier)
  }

  def fetchFieldValues(
      clientId: ClientId,
      context: ApiContext,
      version: ApiVersionNbr
    )(implicit hc: HeaderCarrier
    ): Future[Seq[SubscriptionFieldValue]] = {

    def getDefinitions() = fetchFieldDefinitions(context, version)

    internalFetchFieldValues(() => getDefinitions())(clientId, ApiIdentifier(context, version))
  }

  private def internalFetchFieldValues(
      getDefinitions: () => Future[Seq[SubscriptionFieldDefinition]]
    )(
      clientId: ClientId,
      apiIdentifier: ApiIdentifier
    )(implicit hc: HeaderCarrier
    ): Future[Seq[SubscriptionFieldValue]] = {

    def joinFieldValuesToDefinitions(
        defs: Seq[SubscriptionFieldDefinition],
        fieldValues: Fields.Alias
      ): Seq[SubscriptionFieldValue] = {
      defs.map(field => SubscriptionFieldValue(field, fieldValues.getOrElse(field.name, FieldValue.empty)))
    }

    def ifDefinitionsGetValues(
        definitions: Seq[SubscriptionFieldDefinition]
      ): Future[Option[ApplicationApiFieldValues]] = {
      if (definitions.isEmpty) {
        Future.successful(None)
      } else {
        fetchApplicationApiValues(clientId, apiIdentifier.context, apiIdentifier.versionNbr)
      }
    }

    for {
      definitions: Seq[SubscriptionFieldDefinition] <- getDefinitions()
      subscriptionFields                            <- ifDefinitionsGetValues(definitions)
      fieldValues                                    = subscriptionFields.fold(Fields.empty)(_.fields)
    } yield joinFieldValuesToDefinitions(definitions, fieldValues)
  }

  import uk.gov.hmrc.http.HttpReads.Implicits._

  def fetchFieldDefinitions(
      apiContext: ApiContext,
      apiVersion: ApiVersionNbr
    )(implicit hc: HeaderCarrier
    ): Future[Seq[SubscriptionFieldDefinition]] = {
    val url = urlSubscriptionFieldDefinition(apiContext, apiVersion)
    logger.debug(s"fetchFieldDefinitions() - About to call $url in ${environment.toString}")
    configureEbridgeIfRequired(
      http.get(url"$url")
    )
      .execute[Option[ApiFieldDefinitions]]
      .map {
        case Some(x) => x.fieldDefinitions.map(toDomain)
        case None    => Seq.empty
      }
  }

  def fetchAllFieldDefinitions()(implicit hc: HeaderCarrier): Future[DefinitionsByApiVersion] = {
    val url = s"$serviceBaseUrl/definition"
    configureEbridgeIfRequired(
      http.get(url"$url")
    )
      .execute[Option[AllApiFieldDefinitions]]
      .map {
        case Some(x) => toDomain(x)
        case None    => DefinitionsByApiVersion.empty
      }
  }

  def saveFieldValues(
      clientId: ClientId,
      apiContext: ApiContext,
      apiVersion: ApiVersionNbr,
      fields: Fields.Alias
    )(implicit hc: HeaderCarrier
    ): Future[ConnectorSaveSubscriptionFieldsResponse] = {
    val url = urlSubscriptionFieldValues(clientId, apiContext, apiVersion)

    configureEbridgeIfRequired(
      http
        .put(url"$url")
        .withBody(Json.toJson(SubscriptionFieldsPutRequest(clientId, apiContext, apiVersion, fields)))
    )
      .execute[HttpResponse]
      .map { response =>
        response.status match {
          case BAD_REQUEST  =>
            Json.parse(response.body).validate[Map[String, String]] match {
              case s: JsSuccess[Map[String, String]] => SaveSubscriptionFieldsFailureResponse(s.get)
              case _                                 => SaveSubscriptionFieldsFailureResponse(Map.empty)
            }
          case OK | CREATED => SaveSubscriptionFieldsSuccessResponse
          case statusCode   => throw UpstreamErrorResponse("Failed to put subscription fields", statusCode)
        }
      }
  }

  def deleteFieldValues(
      clientId: ClientId,
      apiContext: ApiContext,
      apiVersion: ApiVersionNbr
    )(implicit hc: HeaderCarrier
    ): Future[FieldsDeleteResult] = {
    val url = urlSubscriptionFieldValues(clientId, apiContext, apiVersion)
    configureEbridgeIfRequired(
      http.delete(url"$url")
    )
      .execute[HttpResponse]
      .map { response =>
        response.status match {
          case NO_CONTENT => FieldsDeleteSuccessResult
          case NOT_FOUND  => FieldsDeleteSuccessResult
          case _          => FieldsDeleteFailureResult
        }
      }
  }

  private def fetchApplicationApiValues(
      clientId: ClientId,
      apiContext: ApiContext,
      apiVersion: ApiVersionNbr
    )(implicit hc: HeaderCarrier
    ): Future[Option[ApplicationApiFieldValues]] = {
    val url = urlSubscriptionFieldValues(clientId, apiContext, apiVersion)
    configureEbridgeIfRequired(
      http.get(url"$url")
    )
      .execute[Option[ApplicationApiFieldValues]]
  }

  private def urlEncode(str: String, encoding: String = "UTF-8") = encode(str, encoding)

  private def urlSubscriptionFieldValues(clientId: ClientId, apiContext: ApiContext, apiVersion: ApiVersionNbr) =
    s"$serviceBaseUrl/field/application/${urlEncode(clientId.value)}/context/${urlEncode(apiContext.value)}/version/${urlEncode(apiVersion.value)}"

  private def urlSubscriptionFieldDefinition(apiContext: ApiContext, apiVersion: ApiVersionNbr) =
    s"$serviceBaseUrl/definition/context/${urlEncode(apiContext.value)}/version/${urlEncode(apiVersion.value)}"
}

private[connectors] object SubscriptionFieldsConnectorDomain {

  def toDomain(f: FieldDefinition): SubscriptionFieldDefinition = {
    SubscriptionFieldDefinition(
      name = f.name,
      description = f.description,
      shortDescription = f.shortDescription,
      `type` = f.`type`,
      hint = f.hint,
      access = f.access
    )
  }

  def toDomain(fs: AllApiFieldDefinitions): DefinitionsByApiVersion = {
    fs.apis
      .map(fd => ApiIdentifier(fd.apiContext, fd.apiVersion) -> fd.fieldDefinitions.map(toDomain))
      .toMap
  }

  case class ApplicationApiFieldValues(
      clientId: ClientId,
      apiContext: ApiContext,
      apiVersion: ApiVersionNbr,
      fieldsId: UUID,
      fields: Map[FieldName, FieldValue]
    )

  case class FieldDefinition(
      name: FieldName,
      description: String,
      shortDescription: String,
      hint: String,
      `type`: String,
      access: AccessRequirements
    )

  case class ApiFieldDefinitions(
      apiContext: ApiContext,
      apiVersion: ApiVersionNbr,
      fieldDefinitions: List[FieldDefinition]
    )

  case class AllApiFieldDefinitions(apis: Seq[ApiFieldDefinitions])

  case class SubscriptionFieldsPutRequest(
      clientId: ClientId,
      apiContext: ApiContext,
      apiVersion: ApiVersionNbr,
      fields: Fields.Alias
    )

}

@Singleton
class SandboxSubscriptionFieldsConnector @Inject() (
    val appConfig: ApplicationConfig,
    val http: HttpClientV2,
    val actorSystem: ActorSystem,
    val futureTimeout: FutureTimeoutSupport
  )(implicit val ec: ExecutionContext
  ) extends AbstractSubscriptionFieldsConnector {

  val environment: Environment = Environment.SANDBOX
  val serviceBaseUrl: String   = appConfig.apiSubscriptionFieldsSandboxUrl
  val useProxy: Boolean        = appConfig.apiSubscriptionFieldsSandboxUseProxy
  val apiKey: String           = appConfig.apiSubscriptionFieldsSandboxApiKey

  lazy val configureEbridgeIfRequired: RequestBuilder => RequestBuilder =
    EbridgeConfigurator.configure(useProxy, apiKey)

}

@Singleton
class ProductionSubscriptionFieldsConnector @Inject() (
    val appConfig: ApplicationConfig,
    val http: HttpClientV2,
    val actorSystem: ActorSystem,
    val futureTimeout: FutureTimeoutSupport
  )(implicit val ec: ExecutionContext
  ) extends AbstractSubscriptionFieldsConnector {

  val environment: Environment = Environment.PRODUCTION
  val serviceBaseUrl: String   = appConfig.apiSubscriptionFieldsProductionUrl

  val configureEbridgeIfRequired: RequestBuilder => RequestBuilder = identity
}
