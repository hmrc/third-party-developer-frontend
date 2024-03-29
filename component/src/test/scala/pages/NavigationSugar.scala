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

package pages

import org.mockito.MockitoSugar
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{Assertion, Assertions}

import uk.gov.hmrc.selenium.webdriver.Driver

import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ApplicationConfig
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.{EncryptedJson, LocalCrypto, PayloadEncryption}

trait NavigationSugar extends Assertions with Matchers with Eventually with MockitoSugar {
  private val mockAppConfig = mock[ApplicationConfig]
  when(mockAppConfig.jsonEncryptionKey).thenReturn("czV2OHkvQj9FKEgrTWJQZVNoVm1ZcTN0Nnc5eiRDJkY=")

  implicit override val patienceConfig: PatienceConfig = PatienceConfig(timeout = scaled(Span(5, Seconds)), interval = scaled(Span(100, Millis)))
  implicit val encryptedJson: EncryptedJson            = new EncryptedJson(new PayloadEncryption()(new LocalCrypto(mockAppConfig)))

  def goOn(page: WebPage): Assertion = {
    go(page)
    on(page)
  }

  def go(webLink: WebLink): Unit = {
    webLink.go()
  }

  def on(page: WebPage): Assertion = {
    eventually {
      withClue(s"Currently in page: ${Driver.instance.getCurrentUrl()}, with title '${page.heading()}' - ") {
        assert(page.isCurrentPage(), s"Page was not loaded: ${page.url()}")
      }
    }
  }

  def anotherTabIsOpened(): Assertion = {
    Driver.instance.getWindowHandles.size() shouldBe 2
  }
}
