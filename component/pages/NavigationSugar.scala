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

import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ApplicationConfig
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.{EncryptedJson, LocalCrypto, PayloadEncryption}
import org.openqa.selenium.WebDriver
import org.scalatest.concurrent.Eventually
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatestplus.selenium.WebBrowser
import org.scalatest.Assertions
import org.mockito.MockitoSugar
import org.scalatest.matchers.should.Matchers

trait NavigationSugar extends WebBrowser with Eventually with Assertions with Matchers with MockitoSugar {
  private val mockAppConfig = mock[ApplicationConfig]
  when(mockAppConfig.jsonEncryptionKey).thenReturn("czV2OHkvQj9FKEgrTWJQZVNoVm1ZcTN0Nnc5eiRDJkY=")

  implicit override val patienceConfig = PatienceConfig(timeout = scaled(Span(3, Seconds)), interval = scaled(Span(100, Millis)))
  implicit val encryptedJson = new EncryptedJson(new PayloadEncryption(new LocalCrypto(mockAppConfig)))

  def goOn(page: WebPage)(implicit webDriver: WebDriver) = {
    go(page)
    on(page)
  }

  def go(page: WebLink)(implicit webDriver: WebDriver) = {
    WebBrowser.go to page
  }

  def on(page: WebPage)(implicit webDriver: WebDriver) = {
    eventually {
      find(tagName("body"))  //.filter(_ => page.isCurrentPage)
    }
    withClue(s"Currently in page: $currentUrl " + find(tagName("h1")).map(_.text).fold(" - ")(h1 => s", with title '$h1' - ")) {
      assert(page.isCurrentPage, s"Page was not loaded: ${page.url}")
    }
  }

  def anotherTabIsOpened()(implicit webDriver: WebDriver) = {
    webDriver.getWindowHandles.size() shouldBe 2
  }
}
