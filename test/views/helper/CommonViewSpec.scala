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

package views.helper

import java.time.Period
import java.util.Locale

import org.scalatest.matchers.should.Matchers
import org.scalatestplus.play.guice.GuiceOneAppPerSuite

import play.api.Application
import play.api.i18n.{Lang, MessagesImpl, MessagesProvider}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.MessagesControllerComponents

import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.GrantLength
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ApplicationConfig
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.controllers.FraudPreventionNavLinkViewModel
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.AsyncHmrcSpec

trait CommonViewSpec extends AsyncHmrcSpec with GuiceOneAppPerSuite with Matchers {
  val mcc                                         = app.injector.instanceOf[MessagesControllerComponents]
  val messagesApi                                 = mcc.messagesApi
  val grantLength: GrantLength                    = GrantLength.EIGHTEEN_MONTHS
  implicit val messagesProvider: MessagesProvider = MessagesImpl(Lang(Locale.ENGLISH), messagesApi)
  implicit val appConfig: ApplicationConfig       = mock[ApplicationConfig]

  def createFraudPreventionNavLinkViewModel(isVisible: Boolean, url: String) = FraudPreventionNavLinkViewModel(isVisible, url)

  when(appConfig.nameOfPrincipalEnvironment).thenReturn("Production")
  when(appConfig.nameOfSubordinateEnvironment).thenReturn("Sandbox")

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .configure(("metrics.jvm", false))
      .build()

}
