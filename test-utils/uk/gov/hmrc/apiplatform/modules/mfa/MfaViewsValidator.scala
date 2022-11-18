/*
 * Copyright 2022 HM Revenue & Customs
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

package uk.gov.hmrc.apiplatform.modules.mfa

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.scalatest.Assertion
import play.api.http.Status
import play.api.mvc.Result
import play.api.test.Helpers.{contentAsString, status}
import play.api.test.Helpers._
import org.scalatest.matchers.should.Matchers._
import scala.concurrent.Future

trait MfaViewsValidator {

  val qrImage = "qrImage"

  def shouldReturnOK(result: Future[Result], f: Document => Assertion) = {
    status(result) shouldBe 200
    val doc = Jsoup.parse(contentAsString(result))
    f(doc)
  }

  def validateSmsSetupReminderView(dom: Document) = {
    dom.getElementById("page-heading").text shouldBe "Get access codes by text"
  }

  def validateSmsSetupSkippedView(dom: Document) = {
    dom.getElementById("page-heading").text shouldBe "Get access codes by text later"
  }

  def validateSmsCompletedPage(dom: Document) = {
    dom.getElementById("page-heading").text shouldBe "You can now get access codes by text"
  }

  def validateSmsAccessCodeView(dom: Document) = {
    dom.getElementById("page-heading").text shouldBe "Enter the access code"
  }

  def validateMobileNumberView(dom: Document) = {
    dom.getElementById("page-heading").text shouldBe "Enter a mobile phone number"
  }

  def validateSelectMfaPage(dom: Document) = {
    dom.getElementById("page-heading").text shouldBe "How do you want to get access codes?"
  }

  def validateSecurityPreferences(dom: Document) = {
    dom.getElementById("page-heading").text shouldBe "Your security preferences"
  }

  def validateAuthAppStartPage(dom: Document) = {
    dom.getElementById("page-heading").text shouldBe "You need an authenticator app on your device"
  }

  def validateQrCodePage(dom: Document): Assertion = {
    dom.getElementById("page-heading").text shouldBe "Set up your authenticator app"
    dom.getElementById("secret").html() shouldBe "abcd efgh"
    dom.getElementById("qrCode").attr("src") shouldBe qrImage
  }

  def validateAuthAppAccessCodePage(dom: Document) = {
    dom.getElementById("page-heading").text shouldBe "Enter your access code"
  }

  def validateNameChangePage(dom: Document) = {
    dom.getElementById("page-heading").text shouldBe "Create a name for your authenticator app"
    dom.getElementById("paragraph").text shouldBe "Use a name that will help you remember the app when you sign in."
    dom.getElementById("name-label").text shouldBe "App Name"
    dom.getElementById("submit").text shouldBe "Continue"
  }

  def validateAuthAppCompletedPage(dom: Document) = {
    dom.getElementById("page-heading").text shouldBe "You can now get access codes by authenticator app"
  }

  def validateErrorTemplateView(result: Future[Result], errorMessage: String) = {
    status(result) shouldBe Status.INTERNAL_SERVER_ERROR
    val doc = Jsoup.parse(contentAsString(result))
    doc.getElementById("page-heading").text shouldBe errorMessage
    doc.getElementById("message").text shouldBe errorMessage
  }
}