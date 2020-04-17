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

package service

import connectors.{ConnectorMetrics, NoopConnectorMetrics}
import controllers.DevHubAuthWrapper
import org.scalatest.Matchers
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.{Application, Mode}
import uk.gov.hmrc.play.test.UnitSpec

class DevHubAuthWrapperSpec extends UnitSpec with Matchers with GuiceOneAppPerSuite  {
  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .overrides(bind[ConnectorMetrics].to[NoopConnectorMetrics])
      .in(Mode.Test)
      .build()

  "decode cookie" should {
    "return session id when it is a valid cookie" in {
      val cookieValue = "5ff9370ed10c6a1c9c12d6aa984cade22c407d22ed777b3a-774f-4ecf-b7ac-d4f9751b0465"

      val expectedSessionId = "ed777b3a-774f-4ecf-b7ac-d4f9751b0465"

      val decoded = DevHubAuthWrapper.decodeCookie(cookieValue)

      decoded shouldBe Some(expectedSessionId)
    }

    "return none when it is not valid cookie" in {
      val invalidCookie = "6ff9370ed10c6a1c9c12d6aa984cade22c407d22ed777b3a-774f-4ecf-b7ac-d4f9751b0465"

      val decoded = DevHubAuthWrapper.decodeCookie(invalidCookie)

      decoded shouldBe None
    }

    "return none when it is very not valid cookie" in {
      val invalidCookie = ""

      val decoded = DevHubAuthWrapper.decodeCookie(invalidCookie)

      decoded shouldBe None
    }
  }

  "encode cookie" in {
    val expectedCookieValue = "5ff9370ed10c6a1c9c12d6aa984cade22c407d22ed777b3a-774f-4ecf-b7ac-d4f9751b0465"

    val sessionId = "ed777b3a-774f-4ecf-b7ac-d4f9751b0465"

    val cookie = DevHubAuthWrapper.encodeCookie(sessionId)

    cookie shouldBe expectedCookieValue
  }
}


