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

package uk.gov.hmrc.thirdpartydeveloperfrontend.domain.services

import uk.gov.hmrc.apiplatform.modules.subscriptionfields.domain.models._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.subscriptions.ApiSubscriptionFields._

trait SubscriptionsJsonFormatters extends ApplicationsJsonFormatters {
  import play.api.libs.json._
  import play.api.libs.functional.syntax._

  implicit val readsSubscriptionFieldDefinition: Reads[SubscriptionFieldDefinition] = (
    (JsPath \ "name").read[FieldName] and
      (JsPath \ "description").read[String] and
      ((JsPath \ "shortDescription").read[String] or Reads.pure("")) and
      ((JsPath \ "hint").read[String] or Reads.pure("")) and
      (JsPath \ "type").read[String] and
      // TODO: Use enums from api-subs-fields
      //  (JsPath \ "type").read[FieldDefinitionType] and
      ((JsPath \ "access").read[AccessRequirements] or Reads.pure(AccessRequirements.Default))
  )(SubscriptionFieldDefinition.apply _)

  implicit val writesSubscriptionFieldDefinition: Writes[SubscriptionFieldDefinition] = new Writes[SubscriptionFieldDefinition] {

    def dropTail[A, B, C, D, E, F](t: Tuple6[A, B, C, D, E, F]): Tuple5[A, B, C, D, E] = (t._1, t._2, t._3, t._4, t._5)

    // This allows us to hide default AccessRequirements from JSON - as this is a rarely used field
    // but not one that business logic would want as an optional field and require getOrElse everywhere.
    override def writes(o: SubscriptionFieldDefinition): JsValue = {
      val common =
        (JsPath \ "name").write[FieldName] and
          (JsPath \ "description").write[String] and
          (JsPath \ "hint").write[String] and
          (JsPath \ "type").write[String] and
          (JsPath \ "shortDescription").write[String]

      (if (o.access == AccessRequirements.Default) {
         (common)(unlift(SubscriptionFieldDefinition.unapply).andThen(dropTail))
       } else {
         (common and (JsPath \ "access").write[AccessRequirements])(unlift(SubscriptionFieldDefinition.unapply))
       }).writes(o)
    }
  }

}

object SubscriptionsJsonFormatters extends SubscriptionsJsonFormatters
