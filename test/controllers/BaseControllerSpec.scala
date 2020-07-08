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

import com.codahale.metrics.SharedMetricRegistries
import config.ApplicationConfig
import mocks.service.ErrorHandlerMock
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.i18n.MessagesApi
import play.api.libs.crypto.CookieSigner
import play.api.mvc.{MessagesControllerComponents, Request}
import play.api.test.{CSRFTokenHelper, FakeRequest}
import uk.gov.hmrc.play.test.UnitSpec
import utils.SharedMetricsClearDown

import scala.concurrent.ExecutionContext

class BaseControllerSpec extends UnitSpec with MockitoSugar with ScalaFutures with GuiceOneAppPerSuite with SharedMetricsClearDown with ErrorHandlerMock {

  SharedMetricRegistries.clear()

//  implicit val ec: ExecutionContext = app.injector.instanceOf[ExecutionContext]

  implicit val appConfig: ApplicationConfig = mock[ApplicationConfig]

  implicit val cookieSigner: CookieSigner = app.injector.instanceOf[CookieSigner]

  implicit lazy val materializer = app.materializer

  lazy val messagesApi = fakeApplication.injector.instanceOf[MessagesApi]

  val mcc = app.injector.instanceOf[MessagesControllerComponents]

  implicit class CSRFRequest[T](request: FakeRequest[T]) {
    def withCSRFToken: FakeRequest[T] = CSRFTokenHelper.addCSRFToken(request).asInstanceOf[FakeRequest[T]]
  }
}
