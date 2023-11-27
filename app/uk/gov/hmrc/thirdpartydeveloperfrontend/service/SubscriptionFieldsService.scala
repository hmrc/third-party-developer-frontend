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

package uk.gov.hmrc.thirdpartydeveloperfrontend.service

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

import cats.implicits._

import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apiplatform.modules.apis.domain.models.ApiDefinition
import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.Collaborator
import uk.gov.hmrc.apiplatform.modules.common.domain.models._
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.ApmConnector
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.Application
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.subscriptions.ApiSubscriptionFields._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.subscriptions.{DevhubAccessLevel, FieldName, FieldValue, Fields}

@Singleton
class SubscriptionFieldsService @Inject() (connectorsWrapper: ConnectorsWrapper, apmConnector: ApmConnector)(implicit val ec: ExecutionContext) {

  def saveFieldValues(
      role: Collaborator.Role,
      application: Application,
      apiContext: ApiContext,
      apiVersion: ApiVersionNbr,
      oldValues: Seq[SubscriptionFieldValue],
      newValues: Fields.Alias
    )(implicit hc: HeaderCarrier
    ): Future[ServiceSaveSubscriptionFieldsResponse] = {
    case object AccessDenied

    def isAllowedToAndCreateNewValue(oldValue: SubscriptionFieldValue, newValue: FieldValue) = {
      if (oldValue.definition.access.devhub.satisfiesWrite(DevhubAccessLevel.fromRole(role))) {
        Right(oldValue.copy(value = newValue))
      } else {
        Left(AccessDenied)
      }
    }

    def doConnectorSave(valuesToSave: Seq[SubscriptionFieldValue]) = {
      val connector    = connectorsWrapper.forEnvironment(application.deployedTo).apiSubscriptionFieldsConnector
      val fieldsToSave = valuesToSave.map(v => (v.definition.name -> v.value)).toMap

      connector.saveFieldValues(application.clientId, apiContext, apiVersion, fieldsToSave)
    }

    if (newValues.isEmpty) {
      Future.successful(SaveSubscriptionFieldsSuccessResponse)
    } else {
      val eitherValuesToSave = oldValues.map(oldValue =>
        newValues.get(oldValue.definition.name) match {
          case Some(newFormValue) => isAllowedToAndCreateNewValue(oldValue, newFormValue)
          case None               => Right(oldValue)
        }
      )

      eitherValuesToSave.toList.sequence.fold(
        accessDenied => Future.successful(SaveSubscriptionFieldsAccessDeniedResponse),
        values => doConnectorSave(values)
      )
    }
  }

  def fetchAllPossibleSubscriptions(applicationId: ApplicationId)(implicit hc: HeaderCarrier): Future[List[ApiDefinition]] = {
    apmConnector.fetchAllPossibleSubscriptions(applicationId)
  }

  def fetchAllFieldDefinitions(environment: Environment)(implicit hc: HeaderCarrier): Future[Map[ApiContext, Map[ApiVersionNbr, Map[FieldName, SubscriptionFieldDefinition]]]] = {
    apmConnector.getAllFieldDefinitions(environment)
  }
}

object SubscriptionFieldsService {

  trait SubscriptionFieldsConnector {
    def fetchFieldValues(clientId: ClientId, context: ApiContext, version: ApiVersionNbr)(implicit hc: HeaderCarrier): Future[Seq[SubscriptionFieldValue]]

    def fetchFieldsValuesWithPrefetchedDefinitions(
        clientId: ClientId,
        apiIdentifier: ApiIdentifier,
        definitionsCache: DefinitionsByApiVersion
      )(implicit hc: HeaderCarrier
      ): Future[Seq[SubscriptionFieldValue]]

    def fetchAllFieldDefinitions()(implicit hc: HeaderCarrier): Future[DefinitionsByApiVersion]

    def fetchFieldDefinitions(apiContext: ApiContext, apiVersion: ApiVersionNbr)(implicit hc: HeaderCarrier): Future[Seq[SubscriptionFieldDefinition]]

    def saveFieldValues(
        clientId: ClientId,
        apiContext: ApiContext,
        apiVersion: ApiVersionNbr,
        fields: Fields.Alias
      )(implicit hc: HeaderCarrier
      ): Future[ConnectorSaveSubscriptionFieldsResponse]

    def deleteFieldValues(clientId: ClientId, apiContext: ApiContext, apiVersion: ApiVersionNbr)(implicit hc: HeaderCarrier): Future[FieldsDeleteResult]
  }

  type DefinitionsByApiVersion = Map[ApiIdentifier, Seq[SubscriptionFieldDefinition]]

  object DefinitionsByApiVersion {
    val empty = Map.empty[ApiIdentifier, Seq[SubscriptionFieldDefinition]]
  }

  sealed trait AccessValidation
  case class ValidateAgainstRole(role: Collaborator.Role) extends AccessValidation
  case object SkipRoleValidation                          extends AccessValidation
}
