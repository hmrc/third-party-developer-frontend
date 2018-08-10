/*
 * Copyright 2018 HM Revenue & Customs
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

import java.util.concurrent.TimeUnit

import component.steps.Env
import org.openqa.selenium._
import org.openqa.selenium.support.ui.{ExpectedCondition, ExpectedConditions, FluentWait, WebDriverWait}

trait PageSugar {

  implicit val webDriver: WebDriver
  val milliseconds = 250

  def waitForElement(by: By) = new WebDriverWait(webDriver, milliseconds).until(
    new ExpectedCondition[WebElement] {
      override def apply(d: WebDriver) = d.findElement(by)
    }
  )

  def waitByWebElement(by: By): WebElement = {
    val wait = new FluentWait[WebDriver](Env.driver).withTimeout(5, TimeUnit.SECONDS).pollingEvery(500, TimeUnit.MILLISECONDS)
      .ignoring(classOf[NoSuchElementException],classOf[StaleElementReferenceException])
    val element = wait.until(ExpectedConditions.visibilityOfElementLocated(by))
    return element
  }

  def waitByWebElementUntilItsClickable(by: By) = {
    val wait = new FluentWait[WebDriver](Env.driver).withTimeout(5, TimeUnit.SECONDS).pollingEvery(500, TimeUnit.MILLISECONDS)
      .ignoring(classOf[NoSuchElementException],classOf[StaleElementReferenceException])
    wait.until(ExpectedConditions.elementToBeClickable(by))
  }
}
