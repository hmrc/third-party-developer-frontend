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

package modules.uplift.domain.models

import domain.models.apidefinitions.ApiIdentifier
import play.api.libs.json.{Format, Json}
import domain.models.apidefinitions.ApiContext
import domain.models.apidefinitions.ApiVersion
import play.api.libs.json._

case class ApiSubscriptions(subscriptions: Map[ApiIdentifier, Boolean] = Map.empty[ApiIdentifier, Boolean]) {
  def isSelected(id: ApiIdentifier): Boolean = subscriptions.get(id).getOrElse(false)
}

object ApiSubscriptions {
  implicit val keyReadsApiIdentifier: KeyReads[ApiIdentifier] = key => 
    key.split("###").toList match {
      case c :: v :: tail => JsSuccess(ApiIdentifier(ApiContext(c), ApiVersion(v.replace("_", "."))))
      case _ => JsError(s"Cannot raise $key to an ApiIdentifier")
    }

  implicit val keyWritesApiIdentifier: KeyWrites[ApiIdentifier] = { id => s"${id.context.value}###${id.version.value.replace(".", "_")}" }

  implicit val format: Format[ApiSubscriptions] = Json.format[ApiSubscriptions]
}






