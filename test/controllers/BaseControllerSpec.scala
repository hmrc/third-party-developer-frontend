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

package controllers

import config.ApplicationConfig
import mocks.service.ErrorHandlerMock
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.i18n.MessagesApi
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.crypto.CookieSigner
import play.api.mvc.MessagesControllerComponents
import utils.AsyncHmrcSpec
import utils.SharedMetricsClearDown

class BaseControllerSpec extends AsyncHmrcSpec with GuiceOneAppPerSuite with SharedMetricsClearDown with ErrorHandlerMock {

  override lazy val app: Application = fakeApplication()

  implicit val appConfig: ApplicationConfig = mock[ApplicationConfig]

  implicit val cookieSigner: CookieSigner = app.injector.instanceOf[CookieSigner]

  implicit lazy val materializer = app.materializer

  lazy val messagesApi = app.injector.instanceOf[MessagesApi]

  val mcc = app.injector.instanceOf[MessagesControllerComponents]

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .configure(("metrics.jvm", false))
      .build()
}
