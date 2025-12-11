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

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration._

import play.api.Mode
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.TestServer
import play.core.server.ServerConfig

trait Env extends BaseSpec {
  import EnvConfig._

  // please do not change this port as it is used for acceptance tests
  // when the service is run with "service manager"
  val stubPort             = sys.env.getOrElse("WIREMOCK_PORT", "11111").toInt
  val stubHost             = "localhost"
  val wireMockUrl          = s"http://$stubHost:$stubPort"

  private val wireMockConfiguration = wireMockConfig().port(stubPort)

  val wireMockServer     = new WireMockServer(wireMockConfiguration)
  var server: TestServer = null

  Runtime.getRuntime addShutdownHook new Thread {

    override def run(): Unit = {
      shutdown()
    }
  }

  def shutdown() = {
    wireMockServer.stop()
    if (server != null) {
      server.stop()
      server = null
    }
  }

  override def beforeEach(): Unit = {
    if(server == null)
      startServer()

    logger.warn("Starting wiremock et al")
    if (!wireMockServer.isRunning) {
      wireMockServer.start()
    }
    WireMock.configureFor(stubHost, stubPort)
    AuditStub.setupAudit()

    startBrowser()
  }

  override def afterEach(): Unit = {
    quitBrowser()

    if (wireMockServer.isRunning) WireMock.reset()

    shutdown()
    super.afterEach()
  }

  def startServer(): Unit = {
    logger.warn("Starting server")
    val application =
      GuiceApplicationBuilder()
        .configure(
          Map(
            "dateOfAdminMfaMandate"                                             -> "2001-01-01",
            "microservice.services.third-party-developer.port"                  -> 11111,
            "microservice.services.third-party-orchestrator.port"               -> 11111,
            "microservice.services.third-party-application-production.port"     -> 11111,
            "microservice.services.third-party-application-sandbox.port"        -> 11111,
            "microservice.services.api-definition.port"                         -> 11111,
            "microservice.services.api-documentation-frontend.port"             -> 11111,
            "microservice.services.third-party-developer-frontend.port"         -> 9685, // This is unused but here for the sake of completion
            "microservice.services.api-platform-deskpro.port"                   -> 11111,
            "microservice.services.api-subscription-fields-production.port"     -> 11111,
            "microservice.services.api-subscription-fields-sandbox.port"        -> 11111,
            "microservice.services.api-platform-microservice.port"              -> 11111,
            "microservice.services.push-pull-notifications-api-production.port" -> 11111,
            "microservice.services.push-pull-notifications-api-sandbox.port"    -> 11111
          )
        )
        .in(Mode.Prod)
        .build()

    val serverConfig = ServerConfig(port = Some(port))
    server = new TestServer(serverConfig, application, None)
    server.start()
  }
}
