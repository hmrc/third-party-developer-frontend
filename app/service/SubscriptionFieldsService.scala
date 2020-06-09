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

import domain.{APIIdentifier, Application, ApplicationNotFound, Environment}
import domain.ApiSubscriptionFields._
import javax.inject.{Inject, Singleton}
import service.SubscriptionFieldsService.DefinitionsByApiVersion
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}
import domain.Role
import domain.DevhubAccessRequirement
import domain.DevhubAccessLevel.Developer
import domain.DevhubAccessLevel
import domain.Role.ADMINISTRATOR
import domain.Role.DEVELOPER
import service.SubscriptionFieldsService._

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

  def saveFieldValues(accessValidation : AccessValidation, application: Application, apiContext: String, apiVersion: String, newValues : Seq[SubscriptionFieldValue])
                        (implicit hc: HeaderCarrier): Future[ServiceSaveSubscriptionFieldsResponse] = {

    def allowedToWriteToAllValues(values: Seq[SubscriptionFieldValue], devhubAccessLevel: DevhubAccessLevel) : Boolean = {
      values.forall(_.definition.access.devhub.satisfiesWrite(devhubAccessLevel))
    }

    def isRoleAllowed : Boolean = {
      accessValidation match {
        case ValidateAgainstRole(role) =>
          val devhubAccessLevel = DevhubAccessLevel.fromRole(role)
           allowedToWriteToAllValues(newValues,devhubAccessLevel)
        case SkipRoleValidation => true
      }
    }

    if (isRoleAllowed) {
      val connector = connectorsWrapper.forEnvironment(application.deployedTo).apiSubscriptionFieldsConnector

      val fieldsToSave = newValues.map(v => (v.definition.name -> v.value)).toMap

      connector
        .saveFieldValues(application.clientId, apiContext, apiVersion, fieldsToSave)
        // .map(r => r match {
        //   case x: SaveSubscriptionFieldsFailureResponse2 => x
        //   // case y: SaveSubscriptionFieldsSuccessResponse2 => y
        // })
    } else { 
      Future.successful(SaveSubscriptionFieldsAccessDeniedResponse)
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
