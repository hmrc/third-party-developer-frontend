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

import javax.inject.Inject
import org.scalatestplus.mockito.MockitoSugar
import play.api.mvc.{Call, RequestHeader}
import play.api.test.FakeRequest
import play.filters.csrf.CSRF.{Token, TokenInfo}
import play.filters.csrf.CSRFConfigProvider
import uk.gov.hmrc.play.test.UnitSpec

class SpliceCSRFTokenSpec @Inject()(csrfConfigProvider: CSRFConfigProvider)
  extends UnitSpec with MockitoSugar {

  trait Setup {
    implicit val requestHeaderWithToken = mock[RequestHeader]
    val csrfConfig = csrfConfigProvider.get
    val token = Token(csrfConfig.tokenName, "token")
  }

  "SpliceCSRFTokenSpec" should {
    "fail if no CSRF token in scope" in {
      implicit val request = FakeRequest()
      val caught = intercept[RuntimeException] {
        SpliceCSRFToken(Call(method = "POST", url = "https://example.com/abcd"))
      }
      caught.getMessage shouldBe "No CSRF token present!"
    }

    "insert a CSRF token to a call at the start of the query string" in new Setup {
      requestHeaderWithToken.addAttr(Token.InfoAttr, TokenInfo(token))
      val call = SpliceCSRFToken(Call(method = "POST", url = "https://example.com/abcd?parameter=efgh"))
      call.url shouldBe "https://example.com/abcd?csrfToken=token&parameter=efgh"
    }

    "add a CSRF token to a call with no query params" in new Setup {
      requestHeaderWithToken.addAttr(Token.InfoAttr, TokenInfo(token))
      val call = SpliceCSRFToken(Call(method = "POST", url = "https://example.com/abcd"))
      call.url shouldBe "https://example.com/abcd?csrfToken=token"
    }

    // TODO: Sort the ReSigned tokens/tags out
//    "add a CSRF token (preferring re-signed request tag) to a call with no query params" in {
//      implicit val rh = mock[RequestHeader]
//      when(rh.tags).thenReturn(Map(NameRequestTag -> "csrfToken", ReSignedRequestTag -> "resigned", RequestTag -> "token"))
//      val call = SpliceCSRFToken(Call(method = "POST", url = "https://example.com/abcd"))
//      call.url shouldBe "https://example.com/abcd?csrfToken=resigned"
//    }
  }
}
