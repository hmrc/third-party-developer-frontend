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

package uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors

import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.apidefinitions.ApiContext
import play.api.libs.json.Json

/*
 * requiresTrust, isTestSupport and versions fields have been deliberately left out as they are not currently required. Adding them back in here should mean 
 * they are automatically deserialised as part of the call to api-platform-microservice.
 */
case class ExtendedApiDefinition(serviceName: String, name: String, description: String, context: ApiContext, categories: List[String])

object ExtendedApiDefinition {
    implicit val formatExtendedApiDefinition = Json.format[ExtendedApiDefinition]
    def toApiDefinition(apiDefinition: ExtendedApiDefinition): ApiDefinition ={
        ApiDefinition(apiDefinition.serviceName, apiDefinition.name, apiDefinition.description, apiDefinition.context, apiDefinition.categories)
    }
}
case class ApiDefinition(serviceName: String, name: String, description: String, context: ApiContext, categories: List[String])

object ApiDefinition {
    implicit val formatApiDefinition = Json.format[ApiDefinition]
}