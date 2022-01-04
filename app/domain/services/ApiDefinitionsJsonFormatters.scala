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

package domain.services

trait ApiDefinitionsJsonFormatters {
  import domain.models.apidefinitions._
  import play.api.libs.json._
  
  implicit val keyReadsApiContext: KeyReads[ApiContext] = key => JsSuccess(ApiContext(key))
  implicit val keyWritesApiContext: KeyWrites[ApiContext] = _.value

  implicit val keyReadsApiVersion: KeyReads[ApiVersion] = key => JsSuccess(ApiVersion(key))
  implicit val keyWritesApiVersion: KeyWrites[ApiVersion] = _.value

  implicit val formatAPIAccess = Json.format[APIAccess]
  implicit val formatApiVersionDefinition = Json.format[ApiVersionDefinition]
  implicit val formatApiIdentifier = Json.format[ApiIdentifier]
}

object ApiDefinitionsJsonFormatters extends ApiDefinitionsJsonFormatters
