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

package component.steps

import java.util.concurrent.TimeUnit

import component.matchers.CustomMatchers
import component.pages._
import cucumber.api.DataTable
import cucumber.api.scala.{EN, ScalaDsl}
import org.openqa.selenium.interactions.Actions
import org.openqa.selenium.{By, WebDriver, WebElement}
import org.scalatest.Matchers

import scala.collection.JavaConversions._
import scala.collection.mutable
import scala.concurrent.duration._

class CommonSteps extends ScalaDsl with EN with Matchers with NavigationSugar with CustomMatchers {

  import scala.collection.JavaConverters._

  implicit val webDriver: WebDriver = Env.driver

  val pages: Map[String, WebPage] = Map(
    "Registration" -> RegistrationPage,
    "Forgotten password" -> ForgotPasswordPage,
    "Reset Password" -> ResetPasswordPage,
    "Successful password reset" -> SignInPage.passwordReset,
    "View all applications" -> ManageApplicationPage,
    "Manage applications empty nest" -> ManageApplicationEmptyPage,
    "Add application" -> AddApplicationPage,
    "Add application success" -> AddApplicationSuccessPage,
    "Unsubscribe API Confirmation" -> UnsubscribeAPIConfirmationPage(""),
    "Change password" -> ChangePasswordPage,
    "Sign in" -> SignInPage.default,
    "Email confirmation" -> EmailConfirmationPage,
    "Resend confirmation" -> ResendConfirmationPage,
    "Email address verified" -> VerificationPage("verificationCode"),
    "Check email" -> CheckEmailPage("john.smith@example.com"),
    "Error" -> ErrorPage,
    "Bad Request" -> BadRequestPage,
    "Manage profile" -> ManageProfilePage,
    "Manage profile success" -> ManageProfileSuccessPage,
    "Edit password" -> EditPasswordPage,
    "Edit password success" -> ChangePasswordSuccessPage,
    "Account locked" -> AccountLockedPage,
    "Logout survey" -> SignOutSurveyPage,
    "Account deletion confirmation" -> AccountDeletionConfirmationPage,
    "Account deletion request submitted" -> AccountDeletionRequestSubmittedPage,
    "Delete application" -> DeleteApplicationPage(""),
    "Delete application confirm" -> DeleteApplicationConfirmPage(""),
    "Delete application complete" -> DeleteApplicationCompletePage("")
  )

  val links: Map[String, WebLink] = Map(
    "Resend verification" -> ResendVerificationLink("john.smith@example.com"),
    "Logout" -> SignOutPage
  ) ++ pages

  Given( """^I try to navigate to the '(.*)' page$""") { (pageName: String) =>
    withClue(s"Fail to go to: $pageName")(go(links(pageName)))
  }

  Given( """^I navigate to the '(.*)' page$""") { (pageName: String) =>
    withClue(s"Fail to load page: $pageName")(goOn(pages(pageName)))
  }

  Given( """^I enter all the fields:$""") { (data: DataTable) =>
    val form: mutable.Map[String, String] = data.asMap(classOf[String], classOf[String]).asScala
    Form.populate(form)
  }

  Given( """^I enter all the fields for name '(.*)':$""") { (name: String, data: DataTable) =>
    val form = data.asList(classOf[String]).asScala
    Form.populateNameArray(name, form)
  }
  Then( """^The application title is '(.*)'$""") { (expectedApplicationTitle: String) =>
    val actualApplicationTitle = webDriver.findElement(By.className("header__menu__proposition-name")).getText
    actualApplicationTitle shouldBe expectedApplicationTitle
  }

  Then( """^The application header colour is 'rgba\((.*)\)'$""") { (expectedHeaderColour: String) =>
    val actualHeaderColour = webDriver.findElement(By.cssSelector(".sandbox .service-info")).getCssValue("border-top-color")
    actualHeaderColour.replace(" ", "") should include(expectedHeaderColour.replace(" ", ""))
  }

  Then( """^I am on the '(.*)' page$""") { (pageName: String) =>
    eventually {
    withClue(s"Fail to be on page: $pageName")(on(pages(pageName))) }
  }

  Then( """^the user-nav header contains a '(.*)' link""") { (linkText: String) =>
    val header = webDriver.findElement(By.id("user-nav-links"))
    header.findElement(By.linkText(linkText)).isDisplayed shouldBe true
  }

  Then( """^The breadcrumb '(.*)' presents as '(.*)' data link$""") { (label: String, dataLink: String) =>
    val breadcrumbElement = webDriver.findElement(By.cssSelector(s"[data-$dataLink]"))
    withClue(s"The breadcrumb with id 'data-$dataLink' does not exists") {
      breadcrumbElement should not be null
    }

    withClue(s"The breadcrumb with id 'data-$dataLink' does not contain the text '$dataLink'") {
      breadcrumbElement.getText shouldBe label
    }
  }

  Then( """^The breadcrumb '(.*)' links to '(.*)' as '(.*)' data link$""") { (label: String, hrefEndsWith: String, dataLink: String) =>
    val breadcrumbElement = webDriver.findElement(By.cssSelector(s"[data-$dataLink]"))
    withClue(s"The breadcrumb with id 'data-$dataLink' does not exists") {
      breadcrumbElement should not be null
    }

    withClue(s"The breadcrumb with id 'data-$dataLink' does not contain the text '$dataLink'") {
      breadcrumbElement.getText shouldBe label
    }

    withClue(s"The breadcrumb with id 'data-$dataLink' does not links to '$hrefEndsWith'") {
      breadcrumbElement.getAttribute("href") should endWith(hrefEndsWith)
    }
  }

  Then( """^I see the field error '(.*)' for the '(.*)'$""") { (errorMessage: String, fieldName: String) =>
    withClue(s"The field '$fieldName' does not have field error: '$errorMessage'") {
      val fieldError = CurrentPage.fieldError(fieldName.toLowerCase.replace(" ", ""))
      fieldError.getText shouldBe errorMessage
    }
  }

  Then( """^I see the field errors:$""") { (fieldErrors: DataTable) =>
    val errors = fieldErrors.asMap(classOf[String], classOf[String]).asScala.toList

    errors.foreach { (row) =>
      val (fieldName, errorMessage) = row
      val fieldError = CurrentPage.fieldError(fieldName.toLowerCase.replace(" ", ""))
      fieldError.getText shouldBe errorMessage
    }
  }

  Then( """^I see the data errors:$""") { (dataErrors: DataTable) =>
    val errors = dataErrors.asMap(classOf[String], classOf[String]).asScala.toList

    errors.foreach { (row) =>
      val (dataName, errorMessage) = row
      val dataError = CurrentPage.dataError(dataName.toLowerCase.replace(" ", ""))
      dataError.getText shouldBe errorMessage
    }
  }

  Then( """^The current page contains link '(.*)' to '(.*)'$""") { (linkText: String, pageName: String) =>
    val link: WebElement = Env.driver.findElement(By.linkText(linkText))
    val page = withClue(s"page not found: $pageName")(pages(pageName))
    link.getAttribute("href") shouldBe page.url
  }

  Then( """^The current page contains (\d+) link '(.*)' to '(.*)'$""") { (expectedNr: Int, linkText: String, pageName: String) =>
    val links: List[WebElement] = Env.driver.findElements(By.linkText(linkText)).asScala.toList
    withClue(s"The page should contain exactly '$expectedNr' links with text '$linkText'") {
      links.length shouldBe expectedNr
    }
    val page = withClue(s"page not found: $pageName")(pages(pageName))
    links.foreach(link => link.getAttribute("href") shouldBe page.url)
  }

  Then( """^I see values in fields:$""") { (fieldData: DataTable) =>
    verifyData(fieldData, _.getAttribute("value"), By.id)
  }

  Then( """^I see values in fields for name '(.*'):$""") { (name: String, fieldData: DataTable) =>
    val values = fieldData.asList(classOf[String])
    val elements = Env.driver.findElements(By.name(name))

    elements.zip(values).foreach { case(element, value) =>
      new TextField(element).value shouldEqual value
    }
  }

  Then( """^I see text in fields:$""") { (fieldData: DataTable) =>
    verifyData(fieldData, _.getText, By.id)
  }

  Then( """^I see data in fields:$""") { (fieldData: DataTable) =>
    verifyData(fieldData, _.getText, s => By.cssSelector(s"[data-$s]"))
  }

  Then( """^I see:$""") { (labels: DataTable) =>
    val textsToFind = labels.raw().flatten.toList
    eventually {
    CurrentPage.bodyText should containInOrder(textsToFind) }
  }

  Then( """^I don't see:$""") { (labels: DataTable) =>
    val textsToFind = labels.raw().flatten.toList
    CurrentPage.bodyText should not contain(textsToFind)
  }

  Then( """^I wait (\d+) seconds and I see:$""") { (seconds: Int,labels: DataTable) =>
    webDriver.manage.timeouts.implicitlyWait(seconds, TimeUnit.SECONDS)
    val textsToFind = labels.raw().flatten.toList
    CurrentPage.bodyText should not contain(textsToFind)
  }

  Then( """^I wait (\d+) seconds and I don't see:$""") { (seconds: Int,labels: DataTable) =>
    webDriver.manage.timeouts.implicitlyWait(seconds, TimeUnit.SECONDS)
    val textsToFind = labels.raw().flatten.toList
    CurrentPage.bodyText should not contain(textsToFind)
  }

  case class RowDataExpectation(rowIndex: Int, fieldDataName: String, expectedValue: String)

  Then( """^I see data rows present:$""") { (dataTable: DataTable) =>
    val dataRows = dataTable.asMaps(classOf[String], classOf[String]).asScala.toList
    val rowDataExpectations = dataRows.zipWithIndex.flatMap(rowWithIndex => for (column <- rowWithIndex._1.entrySet()) yield
      RowDataExpectation(rowWithIndex._2, column.getKey, column.getValue))
    rowDataExpectations.foreach(verifyDataInRow)
  }

  def verifyDataInRow(rowDataExpectation: RowDataExpectation): Unit = {
    val body = Env.driver.findElement(By.tagName("body"))
    withClue(s"The field '${rowDataExpectation.fieldDataName}' does not have value: '${rowDataExpectation.expectedValue}'") {
      body.findElements(By.cssSelector(s"[data-${rowDataExpectation.fieldDataName}]"))(rowDataExpectation.rowIndex).getText shouldBe
        rowDataExpectation.expectedValue
    }
  }

  Then( """^I should not see fields:$""") { (fields: DataTable) =>
    verifyNotPresent(fields.asList(classOf[String]).asScala)
  }

  Then( """^the (.*) field contains (.*)$""") { (field: String, expectedText: String) =>
    val formFieldValue =
      webDriver.findElement(By.id(field)).getAttribute("value")

    formFieldValue shouldBe expectedText
  }

  Then( """^I am sent to the url (.*)$""") { url: String =>
      webDriver.getCurrentUrl should fullyMatch regex s""".*?:\\d+$url"""
  }

  def verifyNotPresent(fields: Seq[String]) = {
    val body = Env.driver.findElement(By.tagName("body"))
    fields.foreach(field => findElement(body, field) shouldBe None)
  }

  def verifyData(fieldData: DataTable, f: WebElement => String, selector: String => By): Unit = {
    val keyValues: mutable.Map[String, String] = fieldData.asMap(classOf[String], classOf[String]).asScala
    val body = Env.driver.findElement(By.tagName("body"))
    keyValues.foreach(keyValue =>
      withClue(s"The field '${keyValue._1}' does not have value: '${keyValue._2}'") {
        eventually (timeout(1 second)){ f(body.findElement(selector(keyValue._1))) shouldBe keyValue._2}
      })
  }

  private def findElement(body: WebElement, id: String): Option[WebElement] = {
    body.findElements(By.id(id)).headOption
  }

  private def findElement(body: WebElement, by: By): Option[WebElement] = {
    body.findElements(by).headOption
  }

  Then( """^I see on current page:$""") { (labels: DataTable) =>
    val textsToFind = labels.raw().flatten.toList
    Env.driver.findElement(By.tagName("body")).getText should containInOrder(textsToFind)
  }

  Then( """^I don't see the following links on the current page:$""") { (labels: DataTable) =>
    val linksToFind = labels.raw().flatten.toList
    val body = Env.driver.findElement(By.tagName("body"))
    linksToFind.foreach {
      linkText => {
        withClue(s"The link '$linkText' should not present on the current page!") {
          findElement(body, By.linkText(linkText)) shouldBe None
        }
      }
    }
  }

  Then( """^I don't see any links on the current page$""") { () =>
    val body = Env.driver.findElement(By.tagName("body"))
    withClue(s"There should be no links present on the current page!") {
      body.findElements(By.tagName("a")) shouldBe empty
    }
  }

  Then( """Pause for (\d+) seconds""") { (seconds: Int) =>
      Thread.sleep(seconds * 1000)
  }

  Then( """^I count (\d+) '(.*)' data link on the current page$""") {
    (expectedNr: Int, linkData: String) =>
      val body = Env.driver.findElement(By.tagName("body"))
      withClue(s"There should be $expectedNr '$linkData' on the current page!") {
        val uris = body.findElements(By.cssSelector(s"[data-$linkData]"))
        uris.length shouldBe expectedNr
      }
  }

  When( """^I click on the '(.*)' link$""") { linkText: String =>
    val link = webDriver.findElement(By.linkText(linkText))
    val actions = new Actions(webDriver)
    actions.moveToElement(link)
    actions.click()
    actions.perform()
  }

  When( """^I click on the button with id '(.*)'$""") { id: String =>
    val link = webDriver.findElement(By.id(id))
    val actions = new Actions(webDriver)
    actions.moveToElement(link)
    actions.click()
    actions.perform()
  }

  When( """^I click on the '(.*)' partial link$""") { linkText: String =>
    val link = webDriver.findElement(By.partialLinkText(linkText))
    val actions = new Actions(webDriver)
    actions.moveToElement(link)
    actions.click()
    actions.perform()
  }

  When( """^I click on the '(.*)' link (\d+) times$""") { (linkText: String, times: Int) =>
    val link = webDriver.findElement(By.linkText(linkText))
    for (i <- 1 to times) link.click()
  }

  When( """^I click on the '(.*)' data link""") { linkData: String =>
    val link = webDriver.findElement(By.cssSelector(s"[data-$linkData]"))
    link.click()
  }

  When( """^I click on the '(.*)' button""") { buttonText: String =>
    val element = webDriver.findElement(By.xpath(s"//button[text()='$buttonText']"))
    val actions = new Actions(webDriver)
    actions.moveToElement(element)
    actions.click()
    actions.perform()
  }

  When( """^I click on the '(.*)' accordion""") { accordionText: String =>
    val accordion = webDriver.findElement(By.xpath(s"//summary[text()='$accordionText']"))
    accordion.click()
  }

  When( """^I click on the '(.*)' radio button""") { buttonText: String =>
    val button = webDriver.findElement(By.xpath(s"//label[contains(., '$buttonText')]/input[@type='radio']"))
    button.click()
  }

  When( """^I click on the radio button with id '(.*)'""") { id: String =>
    val button = webDriver.findElement(By.id(id))
    button.click()
  }

  When("""^I select the '(.*)' checkbox""") {checkBoxText: String =>
    val checkbox = webDriver.findElement(By.id(checkBoxText))
    checkbox.click()
  }

  When( """^I see on the current page: '(.*)'$""") { contents: String =>
    webDriver.findElement(By.tagName("body")).getText.split('\n') should contain(contents)
  }

  When( """^The current page contains a link '(.*)'$""") { (linkText: String) =>
    val link: WebElement = Env.driver.findElement(By.linkText(linkText))
    link should not be null
  }

  When( """^The action with the name '(.*)' has the status '(.*)'$""") {  (actionName: String, status: String) =>
    webDriver.getPageSource.contains(actionName) shouldBe true

    val rows: List[WebElement] = webDriver.findElements(By.tagName("tr")).toList
    rows.foreach {
      row => {
        val tds: List[WebElement] = row.findElements(By.tagName("td")).toList

        val actions = tds.filter(td => td.getText == actionName)

        actions.foreach {
          _ => {
            tds.size shouldBe 2
            tds.get(0).getText shouldBe actionName
            tds.get(1).getText shouldBe status
          }
        }
      }
    }
  }
}
