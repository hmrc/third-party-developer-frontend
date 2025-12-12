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

import java.time.Duration
import scala.jdk.CollectionConverters._

import org.openqa.selenium.support.ui.{ExpectedConditions, FluentWait, Wait}
import org.openqa.selenium.{By, WebDriver, WebElement}

import uk.gov.hmrc.selenium.component.PageObject
import uk.gov.hmrc.selenium.webdriver.Driver

case class Link(href: String, text: String)

trait WebLink extends PageObject {
  def url(): String

  def go(): Unit = get(this.url())

  protected def waitForElementToBePresent(locator: By): WebElement = {
    fluentWait.until(ExpectedConditions.presenceOfElementLocated(locator))
  }

  protected def fluentWait: Wait[WebDriver] = new FluentWait[WebDriver](Driver.instance)
    .withTimeout(Duration.ofSeconds(3))
    .pollingEvery(Duration.ofSeconds(1))
}

trait WebPage extends WebLink {

  val pageHeading: String

  def heading() = getText(By.tagName("h1"))

  def bodyText() = getText(By.tagName("body"))

  def isCurrentPage(): Boolean = this.heading() == this.pageHeading

  protected def findElements(location: By): List[WebElement] = {
    Driver.instance.findElements(location).asScala.toList
  }

  protected def findElement(location: By): Option[WebElement] = {
    findElements(location).headOption
  }
}

object AnyWebPageWithUserLinks extends PageObject {

  private def findElements(location: By): List[WebElement] = {
    Driver.instance.findElements(location).asScala.toList
  }

  def navLinks() = findElements(By.id("user-nav-links")).headOption

  def userLink(userFullName: String) = navLinks().flatMap(_.findElements(By.linkText(userFullName)).asScala.headOption)
}
