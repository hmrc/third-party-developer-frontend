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

package service

import cats.implicits._
import domain.models.subscriptions.ApiSubscriptionFields._
import domain.models.apidefinitions.APIIdentifier
import domain.models.applications.{Application, Environment, Role}
import domain.models.subscriptions.DevhubAccessLevel
import javax.inject.{Inject, Singleton}
import service.SubscriptionFieldsService.DefinitionsByApiVersion
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SubscriptionFieldsService @Inject()(connectorsWrapper: ConnectorsWrapper)(implicit val ec: ExecutionContext) {

  def fetchFieldsValues(application: Application, fieldDefinitions: Seq[SubscriptionFieldDefinition], apiIdentifier: APIIdentifier)
                       (implicit hc: HeaderCarrier): Future[Seq[SubscriptionFieldValue]] = {
    val connector = connectorsWrapper.forEnvironment(application.deployedTo).apiSubscriptionFieldsConnector

    if (fieldDefinitions.isEmpty) {
      Future.successful(Seq.empty[SubscriptionFieldValue])
    } else {
      connector.fetchFieldValues(application.clientId, apiIdentifier.context, apiIdentifier.version)
    }
  }

  def saveFieldValues(  role : Role,
                        application : Application,
                        apiContext: String,
                        apiVersion : String,
                        oldValues: Seq[SubscriptionFieldValue],
                        newValues: Map[String, String])
                        (implicit hc: HeaderCarrier) : Future[ServiceSaveSubscriptionFieldsResponse] = {
    case object AccessDenied

    def isAllowedToAndCreateNewValue(oldValue: SubscriptionFieldValue, newValue: String) = {
      if (oldValue.definition.access.devhub.satisfiesWrite(DevhubAccessLevel.fromRole(role))) {
        Right(oldValue.copy(value = newValue))
      } else {
        Left(AccessDenied)
      }
    }

    def doConnectorSave(valuesToSave: Seq[SubscriptionFieldValue]) = {
      val connector = connectorsWrapper.forEnvironment(application.deployedTo).apiSubscriptionFieldsConnector
      val fieldsToSave = valuesToSave.map(v => (v.definition.name -> v.value)).toMap

      connector.saveFieldValues(application.clientId, apiContext, apiVersion, fieldsToSave)
    }
    
    if (newValues.isEmpty) {
        Future.successful(SaveSubscriptionFieldsSuccessResponse)
    } else {
      val eitherValuesToSave = oldValues.map(oldValue =>
        newValues.get(oldValue.definition.name) match {
          case Some(newFormValue) => isAllowedToAndCreateNewValue(oldValue, newFormValue)
          case None => Right(oldValue)
        }
      )

      eitherValuesToSave.toList.sequence.fold(
        accessDenied => Future.successful(SaveSubscriptionFieldsAccessDeniedResponse),
        values => doConnectorSave(values)
      )
    }
  }
  
  def saveBlankFieldValues( application: Application,
                            apiContext: String,
                            apiVersion: String,
                            values : Seq[SubscriptionFieldValue])
                            (implicit hc: HeaderCarrier) : Future[ServiceSaveSubscriptionFieldsResponse] = {

    def createEmptyFieldValues(fieldDefinitions: Seq[SubscriptionFieldDefinition]) = {
      fieldDefinitions
        .map(d => d.name -> "")
        .toMap
    }

    if(values.forall(_.value.isEmpty)){
      val connector = connectorsWrapper.forEnvironment(application.deployedTo).apiSubscriptionFieldsConnector

      val emptyFieldValues = createEmptyFieldValues(values.map(_.definition))

      connector.saveFieldValues(application.clientId, apiContext, apiVersion, emptyFieldValues)
    } else {
      Future.successful(SaveSubscriptionFieldsSuccessResponse)
    }
  }

  def getAllFieldDefinitions(environment: Environment)(implicit hc: HeaderCarrier): Future[DefinitionsByApiVersion] = {
    connectorsWrapper
      .forEnvironment(environment)
      .apiSubscriptionFieldsConnector.fetchAllFieldDefinitions()
  }

  def getFieldDefinitions(application: Application, apiIdentifier: APIIdentifier)(implicit hc: HeaderCarrier): Future[Seq[SubscriptionFieldDefinition]] = {
    val connector = connectorsWrapper.forEnvironment(application.deployedTo).apiSubscriptionFieldsConnector

    connector.fetchFieldDefinitions(apiIdentifier.context, apiIdentifier.version)
  }
}

object SubscriptionFieldsService {
  trait SubscriptionFieldsConnector {
    def fetchFieldValues(clientId: String, context: String, version: String)
                        (implicit hc: HeaderCarrier) : Future[Seq[SubscriptionFieldValue]]

    def fetchFieldsValuesWithPrefetchedDefinitions(clientId: String, apiIdentifier: APIIdentifier, definitionsCache: DefinitionsByApiVersion)
                                                  (implicit hc: HeaderCarrier): Future[Seq[SubscriptionFieldValue]]

    def fetchAllFieldDefinitions()(implicit hc: HeaderCarrier): Future[DefinitionsByApiVersion]

    def fetchFieldDefinitions(apiContext: String, apiVersion: String)
                             (implicit hc: HeaderCarrier): Future[Seq[SubscriptionFieldDefinition]]

    def saveFieldValues(clientId: String, apiContext: String, apiVersion: String, fields: Fields)
                       (implicit hc: HeaderCarrier): Future[ConnectorSaveSubscriptionFieldsResponse]

    def deleteFieldValues(clientId: String, apiContext: String, apiVersion: String)(implicit hc: HeaderCarrier): Future[FieldsDeleteResult]
  }

  type DefinitionsByApiVersion = Map[APIIdentifier, Seq[SubscriptionFieldDefinition]]

  object DefinitionsByApiVersion {
    val empty = Map.empty[APIIdentifier, Seq[SubscriptionFieldDefinition]]
  }

  sealed trait AccessValidation
  case class ValidateAgainstRole(role: Role) extends AccessValidation
  case object SkipRoleValidation extends AccessValidation
}
