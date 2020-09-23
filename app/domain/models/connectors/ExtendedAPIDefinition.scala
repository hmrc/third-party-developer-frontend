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

package domain.models.connectors

import domain.models.apidefinitions.ApiContext
import play.api.libs.json.Json

/*
 * requiresTrust, isTestSupport and versions fields have been deliberately left out as they are not currently required. Adding them back in here should mean 
 * they are automatically deserialised as part of the call to api-platform-microservice.
*/
case class ExtendedAPIDefinition(serviceName: String, name: String, description: String, context: ApiContext)

object ExtendedAPIDefinition {
    implicit val formatExtendedAPIDefintion = Json.format[ExtendedAPIDefinition]
}