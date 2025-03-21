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

package uk.gov.hmrc.thirdpartydeveloperfrontend.controllers

import org.apache.pekko.stream.Materializer
import org.scalatestplus.play.guice.GuiceOneAppPerSuite

import play.api.Application
import play.api.i18n.MessagesApi
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.crypto.CookieSigner
import play.api.mvc.MessagesControllerComponents
import play.api.test.FakeRequest
import play.filters.csrf.CSRF.TokenProvider

import uk.gov.hmrc.apiplatform.modules.common.utils.FixedClock
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.{ApplicationConfig, FraudPreventionConfig}
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.service.{ErrorHandlerMock, SessionServiceMock}
import uk.gov.hmrc.thirdpartydeveloperfrontend.security.CookieEncoding
import uk.gov.hmrc.thirdpartydeveloperfrontend.testdata.CommonSessionFixtures
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.AsyncHmrcSpec
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithLoggedInSession._

abstract class BaseControllerSpec
    extends AsyncHmrcSpec
    with GuiceOneAppPerSuite
    with ErrorHandlerMock
    with SessionServiceMock
    with CommonSessionFixtures
    with CookieEncoding
    with FixedClock {

  implicit val appConfig: ApplicationConfig = mock[ApplicationConfig]
  when(appConfig.nameOfPrincipalEnvironment).thenReturn("Production")
  when(appConfig.nameOfSubordinateEnvironment).thenReturn("Sandbox")

  def fraudPreventionConfig: FraudPreventionConfig = FraudPreventionConfig(enabled = false, List.empty, "")
  implicit val cookieSigner: CookieSigner          = app.injector.instanceOf[CookieSigner]

  implicit lazy val materializer: Materializer = app.materializer

  lazy val messagesApi: MessagesApi = app.injector.instanceOf[MessagesApi]

  val sessionParams = Seq("csrfToken" -> app.injector.instanceOf[TokenProvider].generateToken)

  val loggedOutRequest = FakeRequest().withSession(sessionParams: _*)

  FetchSessionById.succeedsWith(devSession.sessionId, devSession)
  UpdateUserFlowSessions.succeedsWith(devSession.sessionId)
  val loggedInDevRequest = FakeRequest().withLoggedIn(this, cookieSigner)(devSession.sessionId).withSession(sessionParams: _*)

  FetchSessionById.succeedsWith(adminSession.sessionId, adminSession)
  UpdateUserFlowSessions.succeedsWith(adminSession.sessionId)
  val loggedInAdminRequest = FakeRequest().withLoggedIn(this, cookieSigner)(adminSession.sessionId).withSession(sessionParams: _*)

  FetchSessionById.succeedsWith(altDevSession.sessionId, altDevSession)
  UpdateUserFlowSessions.succeedsWith(altDevSession.sessionId)
  val loggedInAltDevRequest = FakeRequest().withLoggedIn(this, cookieSigner)(altDevSession.sessionId).withSession(sessionParams: _*)

  FetchSessionById.succeedsWith(partLoggedInSession.sessionId, partLoggedInSession)
  val partLoggedInRequest = FakeRequest().withLoggedIn(this, cookieSigner)(partLoggedInSession.sessionId)

  val mcc: MessagesControllerComponents = app.injector.instanceOf[MessagesControllerComponents]

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .configure(("metrics.jvm", false))
      .build()
}
