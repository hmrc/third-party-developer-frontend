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

import connectors.ThirdPartyDeveloperConnector
import org.scalatest.Matchers
import play.api.http.Status.INTERNAL_SERVER_ERROR
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
import utils.AsyncHmrcSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future.{failed, successful}

class MFAServiceSpec extends AsyncHmrcSpec with Matchers {

  trait Setup {
    val email = "bob.smith@example.com"
    val totpCode = "12345678"
    val connector = mock[ThirdPartyDeveloperConnector]

    when(connector.enableMfa(eqTo(email))(any[HeaderCarrier])).thenReturn(successful(()))
    when(connector.removeMfa(eqTo(email))(any[HeaderCarrier])).thenReturn(successful(()))

    val service = new MFAService(connector)
  }

  trait FailedTotpVerification extends Setup {
    when(connector.verifyMfa(eqTo(email), eqTo(totpCode))(any[HeaderCarrier])).thenReturn(successful(false))
  }

  trait SuccessfulTotpVerification extends Setup {
    when(connector.verifyMfa(eqTo(email), eqTo(totpCode))(any[HeaderCarrier])).thenReturn(successful(true))
  }

  "enableMfa" should {
    "return failed totp when totp verification fails" in new FailedTotpVerification {
      val result = await(service.enableMfa(email, totpCode)(HeaderCarrier()))
      result.totpVerified shouldBe false
    }

    "not call enable mfa when totp verification fails" in new FailedTotpVerification {
      await(service.enableMfa(email, totpCode)(HeaderCarrier()))
      verify(connector, never).enableMfa(eqTo(email))(any[HeaderCarrier])
    }

    "return successful totp when totp verification passes" in new SuccessfulTotpVerification {
      val result = await(service.enableMfa(email, totpCode)(HeaderCarrier()))
      result.totpVerified shouldBe true
    }

    "enable MFA totp when totp verification passes" in new SuccessfulTotpVerification {
      await(service.enableMfa(email, totpCode)(HeaderCarrier()))
      verify(connector, times(1)).enableMfa(eqTo(email))(any[HeaderCarrier])
    }

    "throw exception if update fails" in new SuccessfulTotpVerification {
      when(connector.enableMfa(eqTo(email))(any[HeaderCarrier]))
        .thenReturn(failed(UpstreamErrorResponse("failed to enable MFA", INTERNAL_SERVER_ERROR, INTERNAL_SERVER_ERROR)))

      intercept[UpstreamErrorResponse](await(service.enableMfa(email, totpCode)(HeaderCarrier())))
    }
  }

  "removeMfa" should {
    "return failed totp when totp verification fails" in new FailedTotpVerification {
      val result: MFAResponse = await(service.removeMfa(email, totpCode)(HeaderCarrier()))
      result.totpVerified shouldBe false
    }

    "not call remove mfa when totp verification fails" in new FailedTotpVerification {
      await(service.removeMfa(email, totpCode)(HeaderCarrier()))
      verify(connector, never).removeMfa(eqTo(email))(any[HeaderCarrier])
    }

    "return successful totp when totp verification passes" in new SuccessfulTotpVerification {
      val result: MFAResponse = await(service.removeMfa(email, totpCode)(HeaderCarrier()))

      result.totpVerified shouldBe true
    }

    "remove MFA when totp verification passes" in new SuccessfulTotpVerification {
      await(service.removeMfa(email, totpCode)(HeaderCarrier()))

      verify(connector, times(1)).removeMfa(eqTo(email))(any[HeaderCarrier])
    }

    "throw exception if removal fails" in new SuccessfulTotpVerification {
      when(connector.removeMfa(eqTo(email))(any[HeaderCarrier]))
        .thenReturn(failed(UpstreamErrorResponse("failed to remove MFA", INTERNAL_SERVER_ERROR, INTERNAL_SERVER_ERROR)))

      intercept[UpstreamErrorResponse](await(service.removeMfa(email, totpCode)(HeaderCarrier())))
    }
  }
}
