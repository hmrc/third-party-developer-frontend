/*
 * Copyright 2019 HM Revenue & Customs
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

package unit.config

import config.{ApplicationConfig, AuthConfigImpl, ErrorHandler}
import org.mockito.BDDMockito.given
import org.scalatest.mockito.MockitoSugar
import play.api.mvc.AnyContentAsEmpty
import play.api.test.{FakeHeaders, FakeRequest}
import service.SessionService
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.ExecutionContext.Implicits.global

class AuthConfigImplSpec extends UnitSpec with MockitoSugar with WithFakeApplication {

  object TestAuthConfigImpl extends AuthConfigImpl {
    val sessionService = mock[SessionService]
    val appConfig = mock[ApplicationConfig]
    val errorHandler = mock[ErrorHandler]
  }

  "Auth Implementation" should {
    given(TestAuthConfigImpl.appConfig.apiDocumentationFrontendUrl).willReturn("http://a.test.url")

    "respond with an http url if the request was http" in {
      val request = FakeRequest("GET", "/", FakeHeaders(), AnyContentAsEmpty, secure = false)
      val out = await(TestAuthConfigImpl.logoutSucceeded(request))
      out.header.headers.getOrElse("Location", "") should startWith("http:")
    }

    "respond with an https url if the request was https" in {
      val request = FakeRequest("GET", "/", FakeHeaders(), AnyContentAsEmpty, secure = true)
      val out = await(TestAuthConfigImpl.logoutSucceeded(request))
      out.header.headers.getOrElse("Location", "") should startWith("https:")
    }
  }
}
