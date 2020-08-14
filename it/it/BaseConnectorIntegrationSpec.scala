/*
 * Copyright 2020 HM Revenue & Customs
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

package it

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration._
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatest.Matchers
import org.scalatest.OptionValues
import org.scalatestplus.play.WsScalaTestClient
import org.scalatest.WordSpec
import play.api.test.DefaultAwaitTimeout
import play.api.test.FutureAwaits

trait BaseConnectorIntegrationSpec
    extends WordSpec
    with Matchers
    with OptionValues
    with WsScalaTestClient
    with DefaultAwaitTimeout
    with FutureAwaits
    with BeforeAndAfterAll
    with BeforeAndAfterEach {
  val stubPort = sys.env.getOrElse("WIREMOCK", "22222").toInt
  val stubHost = "localhost"
  val wireMockUrl = s"http://$stubHost:$stubPort"
  val wireMockServer = new WireMockServer(wireMockConfig().port(stubPort))

  override def beforeAll() {
    wireMockServer.start()
    WireMock.configureFor(stubHost, stubPort)
  }

  override def afterAll() {
    wireMockServer.stop()
  }

  override def beforeEach() {
    wireMockServer.resetMappings()
  }

}
