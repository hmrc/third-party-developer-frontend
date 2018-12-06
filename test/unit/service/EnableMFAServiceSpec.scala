/*
 * Copyright 2018 HM Revenue & Customs
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

package unit.service

import connectors.ThirdPartyDeveloperConnector
import org.scalatest.Matchers
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.mockito.Matchers.{any, eq => mockEq}
import play.api.http.Status
import service.EnableMFAService
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec

class EnableMFAServiceSpec extends UnitSpec with Matchers with MockitoSugar {

  trait Setup {
    val email = "bob.smith@example.com"
    val totpCode = "12345678"
    val connector = mock[ThirdPartyDeveloperConnector]

    when(connector.enableMfa(mockEq(email))(any[HeaderCarrier])).thenReturn(Status.NO_CONTENT)

    val service = new EnableMFAService {
      val tpdConnector = connector
    }
  }

  trait FailedTotpVerification extends Setup {
    when(connector.verifyMfa(mockEq(email), mockEq(totpCode))(any[HeaderCarrier])).thenReturn(false)
    val result = service.enableMfa(email, totpCode)(new HeaderCarrier())
  }

  trait SuccessfulTotpVerification extends Setup {
    when(connector.verifyMfa(mockEq(email), mockEq(totpCode))(any[HeaderCarrier])).thenReturn(true)
    val result = service.enableMfa(email, totpCode)(new HeaderCarrier())
  }

  "enableMfa" should {
    "return failed totp when totp verification fails" in new FailedTotpVerification {
      result.totpVerified shouldBe false
    }

    "not call enable mfa when totp verification fails" in new FailedTotpVerification {
      verify(connector, never).enableMfa(mockEq(email))(any[HeaderCarrier])
    }

    "return successful totp when totp verification passes" in new SuccessfulTotpVerification {
      result.totpVerified shouldBe true
    }

    "enable MFA totp when totp verification passes" in new SuccessfulTotpVerification {
      verify(connector, times(1)).enableMfa(mockEq(email))(any[HeaderCarrier])
    }

  }
}
