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

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration._

import uk.gov.hmrc.apiplatform.modules.common.services.ApplicationLogger

object WiremockInstance extends ApplicationLogger {

  // please do not change this port as it is used for acceptance tests
  // when the service is run with "service manager"
  val stubPort    = sys.env.getOrElse("WIREMOCK_PORT", "11111").toInt
  val stubHost    = "localhost"
  val wireMockUrl = s"http://$stubHost:$stubPort"

  private val wireMockConfiguration = wireMockConfig().port(stubPort)

  val wireMockServer = new WireMockServer(wireMockConfiguration)

  def ensureIsRunning() = {
    if (!wireMockServer.isRunning) {
      logger.warn("Starting wiremock et al")
      wireMockServer.start()
      WireMock.configureFor(stubHost, stubPort)
    }
  }

  def reset() = {
    if (wireMockServer.isRunning)
      WireMock.reset()
  }
}
