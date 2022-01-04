/*
 * Copyright 2022 HM Revenue & Customs
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

import domain.services.ApplicationsJsonFormatters
import domain.services.AccessRequirementsJsonFormatters
import domain.models.subscriptions.{AccessRequirements,FieldName}
import domain.services.SubscriptionsJsonFormatters

object SubscriptionFieldsConnectorJsonFormatters
    extends ApplicationsJsonFormatters
    with SubscriptionsJsonFormatters
    with AccessRequirementsJsonFormatters {

  import SubscriptionFieldsConnectorDomain._
  import play.api.libs.json._
  import play.api.libs.functional.syntax._

  implicit val readsFieldDefinition: Reads[FieldDefinition] = (
    (JsPath \ "name").read[FieldName] and
      (JsPath \ "description").read[String] and
      ((JsPath \ "shortDescription").read[String] or Reads.pure("")) and
      ((JsPath \ "hint").read[String] or Reads.pure("")) and
      // TODO: Below TODO to be deleted when SubscriptionFieldsConnector is no longer used
      //  as calls to api-subs-fields will in the future go through APM
      // TODO: Use enums from api-subs-fields
      //  (JsPath \ "type").read[FieldDefinitionType] and
      (JsPath \ "type").read[String] and
      ((JsPath \ "access").read[AccessRequirements] or Reads.pure(AccessRequirements.Default))
  )(FieldDefinition.apply _)

  implicit val formatSubscriptionFieldsPutRequest: Format[SubscriptionFieldsPutRequest] = Json.format[SubscriptionFieldsPutRequest]

  implicit val readsApiFieldDefinitions: Reads[ApiFieldDefinitions] = Json.reads[ApiFieldDefinitions]

  implicit val readsApplicationApiFieldValues: Reads[ApplicationApiFieldValues] = Json.reads[ApplicationApiFieldValues]

  implicit val formatAllApiFieldDefinitionsResponse: Reads[AllApiFieldDefinitions] = Json.reads[AllApiFieldDefinitions]
}
