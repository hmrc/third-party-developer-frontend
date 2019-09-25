/*
 * Copyright 2019 HM Revenue & Customs
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

package component.steps


import java.io.{File, IOException}
import java.net.URL
import java.util.Calendar

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.common.ConsoleNotifier
import com.github.tomakehurst.wiremock.core.WireMockConfiguration._
import component.stubs.AuditStub
import cucumber.api.scala.{EN, ScalaDsl}
import org.apache.commons.io.FileUtils
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.firefox.{FirefoxDriver, FirefoxProfile}
import org.openqa.selenium.remote.{DesiredCapabilities, RemoteWebDriver}
import org.openqa.selenium.{Dimension, OutputType, TakesScreenshot, WebDriver}
import org.scalatest.Matchers
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.TestServer
import play.api.{Logger, Mode}
import utils.BrowserStackCaps

import scala.util.{Properties, Try}


trait Env extends ScalaDsl with EN with Matchers with BrowserStackCaps {
  var passedTestCount: Int = 0
  var failedTestCount: Int = 0
  // please do not change this port as it is used for acceptance tests
  // when the service is run with "service manager"
  val port = 6001
  val host = s"http://localhost:$port"
  val stubPort = sys.env.getOrElse("WIREMOCK_PORT", "11111").toInt
  val stubHost = "localhost"
  val wireMockUrl = s"http://$stubHost:$stubPort"

  private val wireMockConfiguration = wireMockConfig()
    .notifier(new ConsoleNotifier(true))
    .port(stubPort)

  val wireMockServer = new WireMockServer(wireMockConfiguration)
  var server: TestServer = null
  lazy val windowSize = new Dimension(1280, 720)

  Runtime.getRuntime addShutdownHook new Thread {
    override def run() {
      shutdown()
    }
  }
  val driver: WebDriver = createWebDriver

  lazy val createWebDriver: WebDriver = {
    Properties.propOrElse("test_driver", "chrome") match {
      case "chrome" => createChromeDriver()
      case "firefox" => createFirefoxDriver()
      case "remote-chrome" => createRemoteChromeDriver()
      case "remote-firefox" => createRemoteFirefoxDriver()
      case other => throw new IllegalArgumentException(s"target browser $other not recognised")
    }
  }

  def createRemoteChromeDriver() = {
    val driver = new RemoteWebDriver(new URL(s"http://localhost:4444/wd/hub"), DesiredCapabilities.chrome)
    driver.manage().window().setSize(windowSize)
    driver
  }

  def createRemoteFirefoxDriver() = {
    new RemoteWebDriver(new URL(s"http://localhost:4444/wd/hub"), DesiredCapabilities.firefox)
  }

  def createChromeDriver(): WebDriver = {
    val driver = new ChromeDriver()
    driver.manage().window().setSize(windowSize)
    driver
  }

  def createFirefoxDriver(): WebDriver = {
    val profile = new FirefoxProfile
    profile.setAcceptUntrustedCertificates(true)
    new FirefoxDriver(profile)
  }

  def javascriptEnabled: Boolean = {
    val jsEnabled: String = System.getProperty("javascriptEnabled", "true")
    if (jsEnabled == null) System.getProperties.setProperty("javascriptEnabled", "true")
    if (jsEnabled != "false") {
      true
    } else {
      false
    }
  }

  def shutdown() = {
    Try(driver.quit())
    wireMockServer.stop()
    if (server != null) server.stop()
  }

  Before { _ =>
    if (!wireMockServer.isRunning) {
      wireMockServer.start()
    }
    WireMock.configureFor(stubHost, stubPort)
    AuditStub.setupAudit()
    driver.manage().deleteAllCookies()
  }


  After(order = 1) { _ =>
    if (wireMockServer.isRunning) WireMock.reset()
  }

  After(order = 2) { scenario =>
    if (scenario.isFailed) {
      val srcFile: Array[Byte] = Env.driver.asInstanceOf[TakesScreenshot].getScreenshotAs(OutputType.BYTES)
      val screenShot: String = "./target/screenshots/" + Calendar.getInstance().getTime + ".png"
      try {
        FileUtils.copyFile(Env.driver.asInstanceOf[TakesScreenshot].getScreenshotAs(OutputType.FILE), new File(screenShot))
      } catch {
        case e: IOException => e.printStackTrace()
      }
      scenario.embed(srcFile, "image/png")
    }
    if (scenario.getStatus.equalsIgnoreCase("passed")) {
      passedTestCount = passedTestCount + 1
    }
    else if (scenario.getStatus.equalsIgnoreCase("failed")) {
      failedTestCount = failedTestCount + 1
    }
    Logger.info("\n*******************************************************************************************************")
    Logger.info("Test -->" + scenario.getName + " is ---> " + scenario.getStatus)
    Logger.info("Passed Test Count ------------>" + passedTestCount)
    Logger.info("Failed Test Count ------------>" + failedTestCount)
    Logger.info("*******************************************************************************************************\n")
  }

  def startServer() {
    val application =
      GuiceApplicationBuilder()
        .configure(
          Map(
            "run.mode" -> "Stub",
            "dateOfAdminMfaMandate" -> "2001-01-01")
        )
        .in(Mode.Prod)
        .build()

    server = new TestServer(port, application)
    server.start()
  }
}

object Env extends Env
