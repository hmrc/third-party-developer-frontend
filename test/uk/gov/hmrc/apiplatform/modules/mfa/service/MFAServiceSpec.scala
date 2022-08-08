/*
 * Copyright 2022 HM Revenue & Customs
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

package uk.gov.hmrc.apiplatform.modules.mfa.service

import play.api.http.Status.INTERNAL_SERVER_ERROR
import uk.gov.hmrc.apiplatform.modules.mfa.connectors.ThirdPartyDeveloperMfaConnector
import uk.gov.hmrc.apiplatform.modules.mfa.models.MfaId
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.UserId
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.AsyncHmrcSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future.{failed, successful}

class MFAServiceSpec extends AsyncHmrcSpec {

  trait Setup {
    val userId = UserId.random
    val mfaId = MfaId.random
    val totpCode = "12345678"
    val connector = mock[ThirdPartyDeveloperMfaConnector]

    when(connector.enableMfa(eqTo(userId))(*)).thenReturn(successful(()))
    when(connector.removeMfaById(eqTo(userId), eqTo(mfaId))(*)).thenReturn(successful(()))

    val service = new MFAService(connector)
  }

  trait FailedTotpVerification extends Setup {
    when(connector.verifyMfa(eqTo(userId), eqTo(totpCode))(*)).thenReturn(successful(false))
  }

  trait SuccessfulTotpVerification extends Setup {
    when(connector.verifyMfa(eqTo(userId), eqTo(totpCode))(*)).thenReturn(successful(true))
  }

  "enableMfa" should {
    "return failed totp when totp verification fails" in new FailedTotpVerification {
      val result = await(service.enableMfa(userId, totpCode)(HeaderCarrier()))
      result.totpVerified shouldBe false
    }

    "not call enable mfa when totp verification fails" in new FailedTotpVerification {
      await(service.enableMfa(userId, totpCode)(HeaderCarrier()))
      verify(connector, never).enableMfa(eqTo(userId))(*)
    }

    "return successful totp when totp verification passes" in new SuccessfulTotpVerification {
      val result = await(service.enableMfa(userId, totpCode)(HeaderCarrier()))
      result.totpVerified shouldBe true
    }

    "enable MFA totp when totp verification passes" in new SuccessfulTotpVerification {
      await(service.enableMfa(userId, totpCode)(HeaderCarrier()))
      verify(connector, times(1)).enableMfa(eqTo(userId))(*)
    }

    "throw exception if update fails" in new SuccessfulTotpVerification {
      when(connector.enableMfa(eqTo(userId))(*))
        .thenReturn(failed(UpstreamErrorResponse("failed to enable MFA", INTERNAL_SERVER_ERROR, INTERNAL_SERVER_ERROR)))

      intercept[UpstreamErrorResponse](await(service.enableMfa(userId, totpCode)(HeaderCarrier())))
    }
  }

  "removeMfaById" should {
    "return failed totp when totp verification fails" in new FailedTotpVerification {
      val result: MFAResponse = await(service.removeMfaById(userId, mfaId, totpCode)(HeaderCarrier()))
      result.totpVerified shouldBe false
    }

    "not call remove mfa when totp verification fails" in new FailedTotpVerification {
      await(service.removeMfaById(userId, mfaId, totpCode)(HeaderCarrier()))
      verify(connector, never).removeMfaById(eqTo(userId), eqTo(mfaId))(*)
    }

    "return successful totp when totp verification passes" in new SuccessfulTotpVerification {
      val result: MFAResponse = await(service.removeMfaById(userId, mfaId, totpCode)(HeaderCarrier()))

      result.totpVerified shouldBe true
    }

    "remove MFA when totp verification passes" in new SuccessfulTotpVerification {
      await(service.removeMfaById(userId, mfaId, totpCode)(HeaderCarrier()))

      verify(connector, times(1)).removeMfaById(eqTo(userId), eqTo(mfaId))(*)
    }

    "throw exception if removal fails" in new SuccessfulTotpVerification {
      when(connector.removeMfaById(eqTo(userId), eqTo(mfaId))(*))
        .thenReturn(failed(UpstreamErrorResponse("failed to remove MFA", INTERNAL_SERVER_ERROR, INTERNAL_SERVER_ERROR)))

      intercept[UpstreamErrorResponse](await(service.removeMfaById(userId, mfaId, totpCode)(HeaderCarrier())))
    }
  }
}
