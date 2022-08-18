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
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.AsyncHmrcSpec

object EndpointScenarioSpec {
  def parseEndpoint(text: String): Option[Endpoint] = {
    text.trim.split("\\s+", 3) match {
      case Array(verb, path, _) if verb.matches("[A-Z]+") => Some(Endpoint(verb, path))
      case _ => None
    }
  }
}

abstract class EndpointScenarioSpec extends AsyncHmrcSpec with GuiceOneAppPerSuite with Writeables with MockConnectors {
  import EndpointScenarioSpec._

  override def fakeApplication() = {
    GuiceApplicationBuilder()
      .overrides(bind[ConnectorMetrics].to[NoopConnectorMetrics])
      .overrides(bind[ThirdPartyDeveloperConnector].toInstance(tpdConnector))
      .overrides(bind[ThirdPartyApplicationProductionConnector].toInstance(tpaProductionConnector))
      .overrides(bind[ThirdPartyApplicationSandboxConnector].toInstance(tpaSandboxConnector))
      .overrides(bind[DeskproConnector].toInstance(deskproConnector))
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

      val result = (requestValues.postBody match {
        case Some(bodyValues) => route(app, CSRFTokenHelper.addCSRFToken(request.withFormUrlEncodedBody(bodyValues.toSeq:_*)))
        case None => route(app, CSRFTokenHelper.addCSRFToken(request))
      }).get

      status(result) match {
        case status: Int if 200 to 299 contains status => Success()
        case status: Int if 300 to 399 contains status => Redirect(redirectLocation(result).get)
        case 400 => BadRequest()
        case 401 => Unauthorized()
        case 423 => Locked()
        case status => Unexpected(status)
      }
    } catch {
      case e: Exception => Error(e.toString)
    }
  }

  // Override these methods within scenarios classes
  def updateRequestForScenario[T](request: FakeRequest[T]): FakeRequest[T] = request

  def getGlobalPathParameterValues(): Map[String,String] = Map.empty
  def getEndpointSpecificPathParameterValues(endpoint: Endpoint): Map[String,String] = Map.empty

  def getEndpointSpecificQueryParameterValues(endpoint: Endpoint): Map[String,String] = Map.empty

  def getEndpointSpecificBodyParameterValues(endpoint: Endpoint): Option[Map[String,String]] = None

  def populateRequestValues(endpoint: Endpoint): Seq[RequestValues] = {
    val pathParameterValues = getGlobalPathParameterValues() ++ getEndpointSpecificPathParameterValues(endpoint)
    val queryParameterValues = getEndpointSpecificQueryParameterValues(endpoint)
    val bodyParameterValues = getEndpointSpecificBodyParameterValues(endpoint)

    List(RequestValues(endpoint, pathParameterValues, queryParameterValues, bodyParameterValues))
  }

  val row = "POST        /login-totp                                                                                  uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.UserLoginAccount.authenticateTotp"
  s"test endpoints when ${describeScenario()}" should {
//    Source.fromFile("conf/app.routes").getLines().flatMap(parseEndpoint).flatMap(populateRequestValues(_)).toSet foreach { requestValues: RequestValues =>
//      List("GET /applications  uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.ManageApplications.manageApps").flatMap(parseEndpoint).flatMap(populateRequestValues(_)).toSet foreach { requestValues: RequestValues =>
//      List("GET  /applications/:id/details uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.Details.details(id: ApplicationId)").flatMap(parseEndpoint).flatMap(populateRequestValues(_)).toSet foreach { requestValues: RequestValues =>
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
