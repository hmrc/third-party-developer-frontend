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
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.endpointauth.preconditions.{HasApplicationData, HasUserData}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.Environment
import uk.gov.hmrc.thirdpartydeveloperfrontend.repositories.FlowRepository
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

abstract class EndpointScenarioSpec extends AsyncHmrcSpec with GuiceOneAppPerSuite with Writeables with MockConnectors with HasApplicationData with HasUserData {
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
      .overrides(bind[ProductionPushPullNotificationsConnector].toInstance(productionPushPullNotificationsConnector))
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
    "context" -> apiContext.value,
    "version" -> apiVersion.value,
    "saveSubsFieldsPageMode"-> "lefthandnavigation",
    "fieldName"-> apiFieldName.value,
    "addTeamMemberPageMode" -> "applicationcheck",
    "teamMemberHash" -> "abc123",
    "file" -> "javascripts/loader.js",
    "clientSecretId" -> "s1id"
  )

  final def getQueryParameterValues(endpoint: Endpoint): Map[String,String] = {
    endpoint match {
      case Endpoint("GET", "/applications/:id/change-locked-subscription") => Map("name" -> applicationName, "context" -> apiContext.value, "version" -> apiVersion.value, "redirectTo" -> redirectUrl)
      case Endpoint("POST", "/applications/:id/change-locked-subscription") => Map("name" -> applicationName, "context" -> apiContext.value, "version" -> apiVersion.value, "redirectTo" -> redirectUrl)
      case Endpoint("GET", "/applications/:id/change-private-subscription") => Map("name" -> applicationName, "context" -> apiContext.value, "version" -> apiVersion.value, "redirectTo" -> redirectUrl)
      case Endpoint("POST", "/applications/:id/change-private-subscription") => Map("name" -> applicationName, "context" -> apiContext.value, "version" -> apiVersion.value, "redirectTo" -> redirectUrl)
      case Endpoint("POST", "/applications/:id/change-subscription") => Map("context" -> apiContext.value, "version" -> apiVersion.value, "redirectTo" -> redirectUrl)
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
      case Endpoint("POST", "/registration") => Map("firstname" -> userFirstName, "lastname" -> userLastName, "emailaddress" -> userEmail, "password" -> userPassword, "confirmpassword" -> userPassword)
      case Endpoint("POST", "/login") => Map("emailaddress" -> userEmail, "password" -> userPassword)
      case Endpoint("POST", "/forgot-password") => Map("emailaddress" -> userEmail)
      case Endpoint("POST", "/login-totp") => Map("accessCode" -> "123456", "rememberMe" -> "false")
      case Endpoint("POST", "/reset-password") => Map("password" -> userPassword, "confirmpassword" -> userPassword)
      case Endpoint("POST", "/support") => Map("fullname" -> s"$userFirstName $userLastName", "emailaddress" -> userEmail, "comments" -> "I am very cross about something")
      case Endpoint("POST", "/applications/:id/check-your-answers/terms-and-conditions") => Map("hasUrl"-> "false")
      case Endpoint("POST", "/applications/:id/team-members/add/:addTeamMemberPageMode") => Map("email"-> userEmail, "role" -> "developer")
      case Endpoint("POST", "/applications/:id/team-members/remove") => Map("email"-> userEmail, "confirm" -> "yes")
      case Endpoint("POST", "/applications/:id/details/change-app-name") => Map("applicationName"-> ("new " + applicationName))
      case Endpoint("POST", "/applications/:id/details/change-privacy-policy-location") => Map("privacyPolicyUrl" -> "http://example.com", "isInDesktop" -> "false", "isNewJourney" -> "true")
      case Endpoint("POST", "/applications/:id/details/change-terms-conditions-location") => Map("termsAndConditionsUrl" -> "http://example.com", "isInDesktop" -> "false", "isNewJourney" -> "true")
      case Endpoint("POST", "/applications/:id/redirect-uris/add") => Map("redirectUri" -> "https://example.com/redirect")
      case Endpoint("POST", "/applications/:id/details/terms-of-use") => Map("termsOfUseAgreed" -> "true")
      case Endpoint("POST", "/applications/:id/redirect-uris/change-confirmation") => Map("originalRedirectUri" -> redirectUrl, "newRedirectUri" -> (redirectUrl + "-new"))
      case Endpoint("POST", "/applications/:id/redirect-uris/delete") => Map("redirectUri" -> redirectUrl, "deleteRedirectConfirm" -> "yes")
      case Endpoint("POST", "/applications/:id/delete-principal") => Map("deleteConfirm" -> "yes")
      case Endpoint("POST", "/applications/:id/ip-allowlist/add") => Map("ipAddress" -> "1.2.3.4/24")
      case Endpoint("POST", "/applications/:id/ip-allowlist/change") => Map("confirm" -> "yes")
      case Endpoint("POST", "/applications/:id/responsible-individual/change/self-or-other") => Map("who" -> "self")
      case Endpoint("POST", "/applications/:id/responsible-individual/change/other") => Map("name" -> "mr responsible", "email" -> "ri@example.com")
      case Endpoint("POST", "/applications/:id/change-subscription") => Map("subscribed" -> "true")
      case Endpoint("POST", "/applications/:id/change-locked-subscription") => Map("subscribed" -> "true", "confirm" -> "true")
      case Endpoint("POST", "/applications/:id/change-private-subscription") => Map("subscribed" -> "true", "confirm" -> "true")
      case Endpoint("POST", "/applications/:id/add/subscription-configuration/:pageNumber") => Map(apiFieldName.value -> apiFieldValue.value)
      case Endpoint("POST", "/applications/:id/api-metadata/:context/:version/:saveSubsFieldsPageMode") => Map(apiFieldName.value -> apiFieldValue.value)
      case Endpoint("POST", "/applications/:id/api-metadata/:context/:version/fields/:fieldName/:saveSubsFieldsPageMode") => Map(apiFieldName.value -> apiFieldValue.value)
      case Endpoint("POST", "/no-applications") => Map("choice" -> "use-apis")
      case Endpoint("POST", "/applications/:id/change-api-subscriptions") => Map("ctx-1_0-subscribed" -> "true")
      case Endpoint("POST", "/applications/:id/sell-resell-or-distribute-your-software") => Map("answer" -> "yes")
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

  val row = "GET         /applications/:id/sell-resell-or-distribute-your-software                                    uk.gov.hmrc.apiplatform.modules.uplift.controllers.UpliftJourneyController.sellResellOrDistributeYourSoftware(id: ApplicationId)"
  s"test endpoints when ${describeScenario()}" should {
    Source.fromFile("conf/app.routes").getLines().flatMap(parseEndpoint).flatMap(populateRequestValues(_)).toSet foreach { requestValues: RequestValues =>
//      List(row).flatMap(parseEndpoint).flatMap(populateRequestValues(_)).toSet foreach { requestValues: RequestValues =>
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
