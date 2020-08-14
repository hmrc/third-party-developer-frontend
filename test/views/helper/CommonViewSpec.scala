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

package views.helper

import java.util.Locale

import config.ApplicationConfig
import org.mockito.Mockito.when
import org.scalatest.Matchers
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.i18n.{Lang, MessagesImpl, MessagesProvider}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.MessagesControllerComponents
import utils.AsyncHmrcSpec
import utils.SharedMetricsClearDown

trait CommonViewSpec extends AsyncHmrcSpec with GuiceOneAppPerSuite with SharedMetricsClearDown with Matchers {
  val mcc = app.injector.instanceOf[MessagesControllerComponents]
  val messagesApi = mcc.messagesApi
  implicit val messagesProvider: MessagesProvider = MessagesImpl(Lang(Locale.ENGLISH), messagesApi)
  implicit val appConfig: ApplicationConfig = mock[ApplicationConfig]

  when(appConfig.nameOfPrincipalEnvironment).thenReturn("Production")
  when(appConfig.nameOfSubordinateEnvironment).thenReturn("Sandbox")

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .configure(("metrics.jvm", false))
      .build()

}
