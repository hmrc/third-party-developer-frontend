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

package uk.gov.hmrc.apiplatform.modules.mfa.service

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future.{failed, successful}

import play.api.http.Status.INTERNAL_SERVER_ERROR
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}

import uk.gov.hmrc.apiplatform.modules.common.domain.models.UserId
import uk.gov.hmrc.apiplatform.modules.mfa.connectors.ThirdPartyDeveloperMfaConnector
import uk.gov.hmrc.apiplatform.modules.mfa.models.MfaId
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.AsyncHmrcSpec

class MfaServiceSpec extends AsyncHmrcSpec {

  trait Setup {
    val userId    = UserId.random
    val mfaId     = MfaId.random
    val totpCode  = "12345678"
    val connector = mock[ThirdPartyDeveloperMfaConnector]

    when(connector.removeMfaById(eqTo(userId), eqTo(mfaId))(*)).thenReturn(successful(()))

    val service = new MfaService(connector)
  }

  trait FailedTotpVerification extends Setup {
    when(connector.verifyMfa(eqTo(userId), eqTo(mfaId), eqTo(totpCode))(*)).thenReturn(successful(false))
  }

  trait SuccessfulTotpVerification extends Setup {
    when(connector.verifyMfa(eqTo(userId), eqTo(mfaId), eqTo(totpCode))(*)).thenReturn(successful(true))
  }

  "enableMfa" should {
    "return failed totp when totp verification fails" in new FailedTotpVerification {
      when(connector.verifyMfa(eqTo(userId), eqTo(mfaId), eqTo(totpCode))(*))
        .thenReturn(successful(false))
      val result = await(service.enableMfa(userId, mfaId, totpCode)(HeaderCarrier()))
      result.totpVerified shouldBe false
    }

    "return successful totp when totp verification passes" in new SuccessfulTotpVerification {
      when(connector.verifyMfa(eqTo(userId), eqTo(mfaId), eqTo(totpCode))(*))
        .thenReturn(successful(true))

      val result = await(service.enableMfa(userId, mfaId, totpCode)(HeaderCarrier()))

      verify(connector).verifyMfa(eqTo(userId), eqTo(mfaId), eqTo(totpCode))(*)
      result.totpVerified shouldBe true
    }

    "throw exception if update fails" in new SuccessfulTotpVerification {
      when(connector.verifyMfa(eqTo(userId), eqTo(mfaId), eqTo(totpCode))(*))
        .thenReturn(failed(UpstreamErrorResponse("failed to enable MFA", INTERNAL_SERVER_ERROR, INTERNAL_SERVER_ERROR)))

      intercept[UpstreamErrorResponse](await(service.enableMfa(userId, mfaId, totpCode)(HeaderCarrier())))
    }
  }

  "removeMfaById" should {
    "return failed totp when totp verification fails" in new FailedTotpVerification {
      val result: MfaResponse = await(service.removeMfaById(userId, mfaId, totpCode, mfaId)(HeaderCarrier()))
      result.totpVerified shouldBe false
    }

    "not call remove mfa when totp verification fails" in new FailedTotpVerification {
      await(service.removeMfaById(userId, mfaId, totpCode, mfaId)(HeaderCarrier()))
      verify(connector, never).removeMfaById(eqTo(userId), eqTo(mfaId))(*)
    }

    "return successful totp when totp verification passes" in new SuccessfulTotpVerification {
      val result: MfaResponse = await(service.removeMfaById(userId, mfaId, totpCode, mfaId)(HeaderCarrier()))

      result.totpVerified shouldBe true
    }

    "remove MFA when totp verification passes" in new SuccessfulTotpVerification {
      await(service.removeMfaById(userId, mfaId, totpCode, mfaId)(HeaderCarrier()))

      verify(connector, times(1)).removeMfaById(eqTo(userId), eqTo(mfaId))(*)
    }

    "throw exception if removal fails" in new SuccessfulTotpVerification {
      when(connector.removeMfaById(eqTo(userId), eqTo(mfaId))(*))
        .thenReturn(failed(UpstreamErrorResponse("failed to remove MFA", INTERNAL_SERVER_ERROR, INTERNAL_SERVER_ERROR)))

      intercept[UpstreamErrorResponse](await(service.removeMfaById(userId, mfaId, totpCode, mfaId)(HeaderCarrier())))
    }
  }
}
