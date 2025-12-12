/*
 * Copyright 2025 HM Revenue & Customs
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

import play.api.test.TestServer
import uk.gov.hmrc.apiplatform.modules.common.services.ApplicationLogger
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.Mode
import play.core.server.ServerConfig

object TpdfeInstance extends ApplicationLogger {
  import EnvConfig._
  logger.info("Loading Server")

  var server: TestServer = null

  def start() = {
    if(server == null) {
      server = startServer()
    }
  }

  private def startServer(): TestServer = {
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
    val localServer = new TestServer(serverConfig, application, None)
    localServer.start()
    localServer
  }
}