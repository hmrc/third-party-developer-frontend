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

package unit.controllers

import java.net.URI

import config.ApplicationConfig
import connectors.ThirdPartyDeveloperConnector
import controllers.MFA
import domain.{Developer, Session}
import org.jsoup.Jsoup
import org.mockito.BDDMockito._
import org.mockito.Matchers
import org.mockito.Matchers.{any, eq => mockEq}
import org.scalatest.mockito.MockitoSugar
import play.api.test.FakeRequest
import qr.{OTPAuthURI, QRCode}
import service.SessionService
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}
import utils.WithLoggedInSession._

import scala.concurrent.Future

class MFASpec extends UnitSpec with MockitoSugar with WithFakeApplication {
  implicit val materializer = fakeApplication.materializer

  trait Setup {
    val secret = "ABCDEFGH"
    val issuer = "HMRC Developer Hub"
    val sessionId = "sessionId"
    val loggedInUser = Developer("johnsmith@example.com", "John", "Doe")
    val qrImage = "qrImage"
    val otpUri = new URI("OTPURI")

    val underTest = new MFA {
      override val connector = mock[ThirdPartyDeveloperConnector]
      override val appConfig = mock[ApplicationConfig]
      override val sessionService = mock[SessionService]
      override val qrCode = mock[QRCode]
      override val otpAuthUri = mock[OTPAuthURI]
    }

    given(underTest.sessionService.fetch(mockEq(sessionId))(any[HeaderCarrier])).willReturn(Future.successful(Some(Session(sessionId, loggedInUser))))
  }

  trait SetupSuccessfulStart2SV extends Setup {
    given(underTest.otpAuthUri.apply(secret.toLowerCase(), issuer, loggedInUser.email)).willReturn(otpUri)
    given(underTest.qrCode.generateDataImageBase64(otpUri.toString)).willReturn(qrImage)
    given(underTest.connector.createMfaSecret(mockEq(loggedInUser.email))(any[HeaderCarrier])).willReturn(secret)
  }

  trait SetupFailedVerification extends Setup {
    val totpCode = "12341234"
    given(underTest.connector.verifyMfa(any[String], any[String])(any[HeaderCarrier])).
      willReturn(Future.successful(false))
  }

  "start2SVSetup" should {
    "return secureAccountSetupPage with secret from third party developer" in new Setup {
      val request = FakeRequest()
        .withLoggedIn(underTest)(sessionId)

      val result = await(underTest.start2SVSetup()(request))

      status(result) shouldBe 200
      val dom = Jsoup.parse(bodyOf(result))
      dom.getElementById("secret").html() shouldBe "abcd efgh"
      dom.getElementById("qrCode").attr("src") shouldBe qrImage
    }
  }

  "enable2SV" should {
    "return error when verification fails" in new SetupFailedVerification {
      
    }
  }
}
