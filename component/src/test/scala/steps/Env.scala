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

package steps

import java.io.{File, IOException}
import java.util.Calendar

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration._
import io.cucumber.scala.{EN, ScalaDsl, Scenario}
import org.apache.commons.io.FileUtils
import org.openqa.selenium.{OutputType, TakesScreenshot}
import org.scalatest.matchers.should.Matchers
import stubs.AuditStub

import play.api.Mode
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.TestServer
import play.core.server.ServerConfig
import uk.gov.hmrc.selenium.webdriver.{Browser, Driver}

import uk.gov.hmrc.apiplatform.modules.common.services.ApplicationLogger

object EnvConfig {
  val port = 6001
  val host = s"http://localhost:$port"
}

object Env extends ScalaDsl with EN with Matchers with ApplicationLogger with Browser {
  import EnvConfig._

  var passedTestCount: Int = 0
  var failedTestCount: Int = 0
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
    if (server != null) server.stop()
  }

  Before(order = 1) { _: Scenario =>
    if (!wireMockServer.isRunning) {
      wireMockServer.start()
    }
    WireMock.configureFor(stubHost, stubPort)
    AuditStub.setupAudit()
  }

  After(order = 1) { _ =>
    if (wireMockServer.isRunning) WireMock.reset()
  }

  After(order = 2) { scenario =>
    if (scenario.isFailed) {
      val srcFile: Array[Byte] = Driver.instance.asInstanceOf[TakesScreenshot].getScreenshotAs(OutputType.BYTES)
      val screenShot: String   = "./target/screenshots/" + Calendar.getInstance().getTime + ".png"

      try {
        FileUtils.copyFile(Driver.instance.asInstanceOf[TakesScreenshot].getScreenshotAs(OutputType.FILE), new File(screenShot))
      } catch {
        case e: IOException => e.printStackTrace()
      }
      scenario.attach(srcFile, "image/png", "attachment")
    }

    if (scenario.getStatus.equals("passed")) {
      passedTestCount = passedTestCount + 1
    } else if (scenario.getStatus.equals("failed")) {
      failedTestCount = failedTestCount + 1
    }
    logger.info("\n*******************************************************************************************************")
    logger.info("Test -->" + scenario.getName + " is ---> " + scenario.getStatus)
    logger.info("Passed Test Count ------------>" + passedTestCount)
    logger.info("Failed Test Count ------------>" + failedTestCount)
    logger.info("*******************************************************************************************************\n")
  }

  def startServer(): Unit = {
    val application =
      GuiceApplicationBuilder()
        .configure(
          Map(
            "dateOfAdminMfaMandate"                                             -> "2001-01-01",
            "microservice.services.third-party-developer.port"                  -> 11111,
            "microservice.services.third-party-developer-session.port"          -> 11111,
            "microservice.services.third-party-application-production.port"     -> 11111,
            "microservice.services.third-party-application-sandbox.port"        -> 11111,
            "microservice.services.api-definition.port"                         -> 11111,
            "microservice.services.api-documentation-frontend.port"             -> 11111,
            "microservice.services.third-party-developer-frontend.port"         -> 9685, // This is unused but here for the sake of completion
            "microservice.services.deskpro-ticket-queue.port"                   -> 11111,
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
