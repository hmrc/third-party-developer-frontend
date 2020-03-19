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

package steps

import org.openqa.selenium._
import org.openqa.selenium.support.ui.{ExpectedCondition, WebDriverWait}

import scala.annotation.tailrec

trait PageSugar {

  implicit val webDriver: WebDriver
  val seconds = 5

  def waitForElement(by: By): WebElement = new WebDriverWait(webDriver, seconds).until(
    new ExpectedCondition[WebElement] {
      override def apply(d: WebDriver) = {
        @tailrec
        def attempt(remaining: Int): WebElement = {
          if (remaining == 0) {
            throw new RuntimeException(s"Retries exhausted whilst trying to find element by $by")
          } else {
            try {
              d.findElement(by)
            }
            catch {
              case _: org.openqa.selenium.StaleElementReferenceException => attempt(remaining - 1)
            }
          }
        }

        attempt(remaining = 5)
      }
    }
  )
}
