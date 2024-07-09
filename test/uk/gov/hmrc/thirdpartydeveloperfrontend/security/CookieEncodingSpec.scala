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

package uk.gov.hmrc.thirdpartydeveloperfrontend.security

import org.scalatestplus.play.guice.GuiceOneAppPerSuite

import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.crypto.CookieSigner
import play.api.mvc.Cookie
import play.api.test.FakeRequest
import play.api.{Application, Mode}

import uk.gov.hmrc.apiplatform.modules.tpd.sessions.domain.models.UserSessionId
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ApplicationConfig
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.{ConnectorMetrics, NoopConnectorMetrics}
import uk.gov.hmrc.thirdpartydeveloperfrontend.security.CookieEncoding
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.AsyncHmrcSpec

class CookieEncodingSpec extends AsyncHmrcSpec with GuiceOneAppPerSuite {

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .overrides(bind[ConnectorMetrics].to[NoopConnectorMetrics])
      .in(Mode.Test)
      .build()

  private val wrapper = new CookieEncoding {
    override val cookieSigner: CookieSigner            = app.injector.instanceOf[CookieSigner]
    implicit override val appConfig: ApplicationConfig = mock[ApplicationConfig]
  }

  "decode cookie for sessionId" should {
    "return session id when it is a valid cookie" in {
      val sessionId = UserSessionId.random

      val request = FakeRequest().withCookies(Cookie("PLAY2AUTH_SESS_ID", wrapper.encodeCookie(sessionId.toString())))

      val decoded = wrapper.extractUserSessionIdFromCookie(request)

      decoded shouldBe Some(sessionId)
    }

    "return none when it is not valid cookie" in {
      val invalidCookie = "6ff9370ed10c6a1c9c12d6aa984cade22c407d22ed777b3a-774f-4ecf-b7ac-d4f9751b0465"

      val request = FakeRequest().withCookies(Cookie("PLAY2AUTH_SESS_ID", invalidCookie))

      val decoded = wrapper.extractUserSessionIdFromCookie(request)

      decoded shouldBe None
    }

    "return none when it is very not valid cookie" in {
      val invalidCookie = ""

      val request = FakeRequest().withCookies(Cookie("PLAY2AUTH_SESS_ID", invalidCookie))

      val decoded = wrapper.extractUserSessionIdFromCookie(request)

      decoded shouldBe None
    }
  }

  "encode cookie" in {
    val expectedCookieValue = "5ff9370ed10c6a1c9c12d6aa984cade22c407d22ed777b3a-774f-4ecf-b7ac-d4f9751b0465"

    val sessionId = "ed777b3a-774f-4ecf-b7ac-d4f9751b0465"

    val cookie = wrapper.encodeCookie(sessionId)

    cookie shouldBe expectedCookieValue
  }
}
