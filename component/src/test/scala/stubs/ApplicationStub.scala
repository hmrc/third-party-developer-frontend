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

import com.github.tomakehurst.wiremock.client.WireMock._

import play.api.http.Status.OK
import play.api.libs.json.Json

import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.ApplicationWithSubscriptions
import uk.gov.hmrc.apiplatform.modules.common.domain.models._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.ApplicationNameValidationJson.ApplicationNameValidationResult
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.ApplicationToken

object ApplicationStub {

  def setupApplicationNameValidation() = {
    val validNameResult = ApplicationNameValidationResult(None)

    Stubs.setupPostRequest("/application/name/validate", OK, Json.toJson(validNameResult).toString)
  }

  def setUpFetchApplication(id: ApplicationId, status: Int, response: String = "") = {
    stubFor(
      get(urlEqualTo(s"/application/${id.value}"))
        .willReturn(aResponse().withStatus(status).withBody(response))
    )
  }

  def setUpFetchEmptySubscriptions(id: ApplicationId, status: Int) = {
    stubFor(
      get(urlEqualTo(s"/application/${id.value}/subscription"))
        .willReturn(aResponse().withStatus(status).withBody("[]"))
    )
  }

  def setUpDeleteSubscription(id: ApplicationId, api: String, version: ApiVersionNbr, status: Int) = {
    stubFor(
      delete(urlEqualTo(s"/application/${id.value}/subscription?context=$api&version=${version.value}"))
        .willReturn(aResponse().withStatus(status))
    )
  }

  def setUpExecuteSubscription(id: ApplicationId, api: String, version: ApiVersionNbr, status: Int) = {
    stubFor(
      post(urlEqualTo(s"/application/${id.value}/subscription"))
        .withRequestBody(equalToJson(Json.toJson(ApiIdentifier(ApiContext(api), version)).toString()))
        .willReturn(aResponse().withStatus(status))
    )
  }

  def setUpUpdateApproval(id: ApplicationId) = {
    stubFor(
      post(urlEqualTo(s"/application/${id.value}/check-information"))
        .willReturn(aResponse().withStatus(OK))
    )
  }

  def configureUserApplications(userId: UserId, applications: List[ApplicationWithSubscriptions] = Nil, status: Int = OK) = {
    import play.api.libs.json.Json

    implicit val writes = Json.writes[ApplicationWithSubscriptions]

    def stubResponse(environment: Environment, applications: List[ApplicationWithSubscriptions]) = {
      stubFor(
        get(urlPathEqualTo("/developer/applications"))
          .withQueryParam("userId", equalTo(userId.toString()))
          .withQueryParam("environment", equalTo(environment.toString))
          .willReturn(
            aResponse()
              .withStatus(status)
              .withBody(Json.toJson(applications).toString())
          )
      )
    }

    val (prodApps, sandboxApps) = applications.partition(_.deployedTo == Environment.PRODUCTION)

    stubResponse(Environment.PRODUCTION, prodApps)
    stubResponse(Environment.SANDBOX, sandboxApps)

    stubFor(
      get(urlPathEqualTo("/api-definitions/all"))
        .withQueryParam("environment", equalTo("SANDBOX"))
        .willReturn(
          aResponse()
            .withStatus(OK)
            .withBody("{}")
        )
    )

    val apisUpliftable = Set.empty[ApiIdentifier]

    stubFor(
      get(urlPathEqualTo("/api-definitions/upliftable"))
        .willReturn(
          aResponse()
            .withBody(Json.toJson(apisUpliftable).toString)
        )
    )

    stubFor(
      get(urlEqualTo("/terms-of-use"))
        .willReturn(
          aResponse
            .withStatus(OK)
            .withBody("[]")
        )
    )
  }

  def configureApplicationCredentials(tokens: Map[String, ApplicationToken], status: Int = OK) = {
    tokens.foreach { entry =>
      stubFor(
        get(urlEqualTo(s"/application/${entry._1}/credentials"))
          .willReturn(
            aResponse()
              .withStatus(status)
              .withBody(Json.toJson(entry._2).toString())
              .withHeader("content-type", "application/json")
          )
      )
    }
  }
}
