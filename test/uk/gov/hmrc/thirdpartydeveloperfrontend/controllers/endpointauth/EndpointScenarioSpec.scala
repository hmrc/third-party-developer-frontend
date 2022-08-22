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

package uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.endpointauth

import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Mode
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers.{redirectLocation, route, status}
import play.api.test.{CSRFTokenHelper, FakeRequest, Writeables}
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors._
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.endpointauth.preconditions.HasApplicationId
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.{ApplicationId, Environment}
import uk.gov.hmrc.thirdpartydeveloperfrontend.repositories.FlowRepository
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.SubscriptionFieldsService.SubscriptionFieldsConnector
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.AsyncHmrcSpec

import scala.io.Source

object EndpointScenarioSpec {
  def parseEndpoint(text: String): Option[Endpoint] = {
    text.trim.split("\\s+", 3) match {
      case Array(verb, path, _) if verb.matches("[A-Z]+") => Some(Endpoint(verb, path))
      case _ => None
    }
  }
}

abstract class EndpointScenarioSpec extends AsyncHmrcSpec with GuiceOneAppPerSuite with Writeables with MockConnectors with HasApplicationId {
  import EndpointScenarioSpec._

  override def fakeApplication() = {
    GuiceApplicationBuilder()
      .overrides(bind[ConnectorMetrics].to[NoopConnectorMetrics])
      .overrides(bind[ThirdPartyDeveloperConnector].toInstance(tpdConnector))
      .overrides(bind[ThirdPartyApplicationProductionConnector].toInstance(tpaProductionConnector))
      .overrides(bind[ThirdPartyApplicationSandboxConnector].toInstance(tpaSandboxConnector))
      .overrides(bind[DeskproConnector].toInstance(deskproConnector))
      .overrides(bind[FlowRepository].toInstance(flowRepository))
      .overrides(bind[ApmConnector].toInstance(apmConnector))
      .overrides(bind[SandboxSubscriptionFieldsConnector].toInstance(sandboxSubsFieldsConnector))
      .overrides(bind[ProductionSubscriptionFieldsConnector].toInstance(productionSubsFieldsConnector))
      .in(Mode.Test)
      .build()
  }

  private def populatePathTemplateWithValues(pathTemplate: String, values: Map[String,String]): String = {
    //TODO fail test if path contains parameters that aren't supplied by the values map
    values.foldLeft(pathTemplate)((path: String, kv: (String,String)) => path.replace(s":${kv._1}", kv._2).replace(s"*${kv._1}", kv._2))
  }

  private def getQueryParameterString(requestValues: RequestValues): String = {
    requestValues.queryParams.map(kv => s"${kv._1}=${kv._2}").mkString("&")
  }

  private def buildRequestPath(requestValues: RequestValues): String = {
    val populatedPathTemplate = populatePathTemplateWithValues(requestValues.endpoint.pathTemplate, requestValues.pathValues)
    val queryParameterString = getQueryParameterString(requestValues)

    s"/developer$populatedPathTemplate${if (queryParameterString.isEmpty) "" else s"?$queryParameterString"}"
  }

  def describeScenario(): String

  def expectedResponses(): ExpectedResponses

  def callEndpoint(requestValues: RequestValues): Response = {
    try {
      val path = buildRequestPath(requestValues)
      val request = updateRequestForScenario(FakeRequest(requestValues.endpoint.verb, path))

      val result = (requestValues.postBody.isEmpty match {
        case false => route(app, CSRFTokenHelper.addCSRFToken(request.withFormUrlEncodedBody(requestValues.postBody.toSeq:_*)))
        case true => route(app, CSRFTokenHelper.addCSRFToken(request))
      }).get

      status(result) match {
        case status: Int if 200 to 299 contains status => Success()
        case status: Int if 300 to 399 contains status => Redirect(redirectLocation(result).get)
        case 400 => BadRequest()
        case 401 => Unauthorized()
        case 403 => Forbidden()
        case 404 => NotFound()
        case 423 => Locked()
        case status => Unexpected(status)
      }
    } catch {
      case e: Exception => Error(e.toString)
    }
  }

  final def getPathParameterValues(): Map[String,String] = Map(
    "id" -> applicationId.value,
    "environment" -> Environment.PRODUCTION.entryName,
    "pageNumber" -> "1",
    "context" -> "ctx",
    "version" -> "1.0",
    "saveSubsFieldsPageMode"-> "lefthandnavigation",
    "fieldName"-> "my_field",
    "addTeamMemberPageMode" -> "applicationcheck",
    "teamMemberHash" -> "abc123",
    "file" -> "javascripts/loader.js",
    "clientSecretId" -> "s1id"
  )

  final def getQueryParameterValues(endpoint: Endpoint): Map[String,String] = {
    endpoint match {
      case Endpoint("GET", "/applications/:id/change-locked-subscription") => Map("name" -> "my-api", "context" -> "ctx", "version" -> "1.0", "redirectTo" -> "http://example.com")
      case Endpoint("POST", "/applications/:id/change-locked-subscription") => Map("name" -> "my-api", "context" -> "ctx", "version" -> "1.0", "redirectTo" -> "http://example.com")
      case Endpoint("GET", "/applications/:id/change-private-subscription") => Map("name" -> "my-api", "context" -> "ctx", "version" -> "1.0", "redirectTo" -> "http://example.com")
      case Endpoint("POST", "/applications/:id/change-private-subscription") => Map("name" -> "my-api", "context" -> "ctx", "version" -> "1.0", "redirectTo" -> "http://example.com")
      case Endpoint("POST", "/applications/:id/change-subscription") => Map("context" -> "ctx", "version" -> "1.0", "redirectTo" -> "http://example.com")
      case Endpoint("GET", "/applications/:id/ip-allowlist/remove") => Map("cidrBlock" -> "192.168.1.2/8")
      case Endpoint("POST", "/applications/:id/ip-allowlist/remove") => Map("cidrBlock" -> "192.168.1.2/8")
      case Endpoint("GET", "/verification") => Map("code" -> "CODE123")
      case Endpoint("GET", "/reset-password-link") => Map("code" -> "1324")
      case Endpoint("GET", "/application-verification") => Map("code" -> "1324")

      case _ => Map.empty
    }
  }

  final def getBodyParameterValues(endpoint: Endpoint): Map[String,String] = {
    endpoint match {
      case Endpoint("POST", "/registration") => Map("firstname" -> "Bob", "lastname" -> "Example", "emailaddress" -> "bob@example.com", "password" -> "S3curE-Pa$$w0rd!", "confirmpassword" -> "S3curE-Pa$$w0rd!")
      case Endpoint("POST", "/login") => Map("emailaddress" -> "bob@example.com", "password" -> "letmein")
      case Endpoint("POST", "/forgot-password") => Map("emailaddress" -> "user@example.com")
      case Endpoint("POST", "/login-totp") => Map("accessCode" -> "123456", "rememberMe" -> "false")
      case Endpoint("POST", "/reset-password") => Map("password" -> "S3curE-Pa$$w0rd!", "confirmpassword" -> "S3curE-Pa$$w0rd!")
      case Endpoint("POST", "/support") => Map("fullname" -> "Bob Example", "emailaddress" -> "bob@example.com", "comments" -> "I am very cross about something")
      case _ => Map.empty
    }
  }

  // Override these methods within scenarios classes
  def updateRequestForScenario[T](request: FakeRequest[T]): FakeRequest[T] = request
  def getPathParameterValueOverrides(endpoint: Endpoint) = Map.empty[String,String]
  def getQueryParameterValueOverrides(endpoint: Endpoint) = Map.empty[String,String]
  def getBodyParameterValueOverrides(endpoint: Endpoint) = Map.empty[String,String]

  def populateRequestValues(endpoint: Endpoint): Seq[RequestValues] = {
    val pathParameterValues = getPathParameterValues() ++ getPathParameterValueOverrides(endpoint)
    val queryParameterValues = getQueryParameterValues(endpoint) ++ getQueryParameterValueOverrides(endpoint)
    val bodyParameterValues = getBodyParameterValues(endpoint) ++ getBodyParameterValueOverrides(endpoint)

    List(RequestValues(endpoint, pathParameterValues, queryParameterValues, bodyParameterValues))
  }

  val row = "GET         /applications/:id/push-secrets                                                               uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.PushPullNotifications.showPushSecrets(id: ApplicationId)"
  s"test endpoints when ${describeScenario()}" should {
//    Source.fromFile("conf/app.routes").getLines().flatMap(parseEndpoint).take(155).flatMap(populateRequestValues(_)).toSet foreach { requestValues: RequestValues =>
      List(row).flatMap(parseEndpoint).flatMap(populateRequestValues(_)).toSet foreach { requestValues: RequestValues =>
      val expectedResponse = expectedResponses.responseOverrides.filter(_.endpoint == requestValues.endpoint) match {
        case rules if rules.size == 1 => rules.head.expectedResponse
        case rules if rules.size > 1 => fail(s"Invalid rule configuration, ${rules.size} rules matched request $requestValues for scenario ${describeScenario()}")
        case _ => expectedResponses.defaultResponse
      }
      s"give $expectedResponse for $requestValues" in {
        val result = callEndpoint(requestValues)
        result shouldBe expectedResponse
      }
    }
  }

}
