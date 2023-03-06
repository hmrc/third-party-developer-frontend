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

package stubs

import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, delete, get, stubFor, urlEqualTo}
import play.api.http.Status.{NOT_FOUND, NO_CONTENT}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.apidefinitions.{ApiContext, ApiVersion}
import uk.gov.hmrc.apiplatform.modules.applications.domain.models.ClientId

import uk.gov.hmrc.apiplatform.modules.applications.domain.models.ClientId
object ApiSubscriptionFieldsStub {

  def setUpDeleteSubscriptionFields(clientId: ClientId, apiContext: ApiContext, apiVersion: ApiVersion) = {
    stubFor(
      delete(urlEqualTo(fieldValuesUrl(clientId, apiContext, apiVersion)))
        .willReturn(aResponse().withStatus(NO_CONTENT))
    )
  }

  private def fieldValuesUrl(clientId: ClientId, apiContext: ApiContext, apiVersion: ApiVersion) = {
    s"/field/application/${clientId.value}/context/${apiContext.value}/version/${apiVersion.value}"
  }

  def noSubscriptionFields(apiContext: ApiContext, version: ApiVersion): Any = {
    stubFor(get(urlEqualTo(fieldDefinitionsUrl(apiContext, version))).willReturn(aResponse().withStatus(NOT_FOUND)))
  }

  private def fieldDefinitionsUrl(apiContext: ApiContext, version: ApiVersion) = {
    s"/definition/context/${apiContext.value}/version/${version.value}"
  }
}
