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
import play.api.mvc.{AnyContentAsEmpty, Request}
import play.api.test.Helpers.{redirectLocation, route, status}
import play.api.test.Writeables
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors._
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.AsyncHmrcSpec

import scala.io.Source

object EndpointScenarioSpec {
  def parseEndpoint(text: String): Option[Endpoint] = {
    text.trim.split("\\s+") match {
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

  def buildRequest(httpVerb: String, requestPath: String): Request[AnyContentAsEmpty.type]

  def describeScenario(): String

  def expectedEndpointResults(): EndpointResults

  def callEndpoint(endpoint: Endpoint): EndpointResult = {
    try {
      val path = s"/developer${endpoint.path}"
      val request = buildRequest(endpoint.verb, path)
      val result = route(app, request).get
      status(result) match {
        case status: Int if 200 to 299 contains status => Success()
        case status: Int if 300 to 399 contains status => Redirect(redirectLocation(result).get)
        case 400 => BadRequest()
        case 423 => Locked()
        case status => Unexpected(status)
      }
    } catch {
      case e: Exception => Error(e)
    }
  }

  s"test endpoints when ${describeScenario()}" should {
    Source.fromFile("conf/app.routes").getLines().flatMap(parseEndpoint).toSet foreach { endpoint: Endpoint =>
//      List("GET /applications  uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.ManageApplications.manageApps").flatMap(parseEndpoint).toSet foreach { endpoint: Endpoint =>
      val expectedResult = expectedEndpointResults.overrides.filter(_.endpoint == endpoint) match {
        case rules if rules.size == 1 => rules.head.result
        case rules if rules.size > 1 => fail(s"Invalid rule configuration, ${rules.size} rules matched endpoint $endpoint for scenario ${describeScenario()}")
        case _ => expectedEndpointResults.defaultResult
      }
      s"give $expectedResult in scenario ${describeScenario()} to access ${endpoint.verb} ${endpoint.path}" in {
        val result = callEndpoint(endpoint)
        result shouldBe expectedResult
      }
    }
  }

}
