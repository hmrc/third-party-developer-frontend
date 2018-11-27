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

  trait SetupFailedTotpVerification extends Setup {
    when(connector.verifyMfa(mockEq(email), mockEq(totpCode))(any[HeaderCarrier])).thenReturn(false)
    val result = service.enableMfa(email, totpCode)(new HeaderCarrier())
  }

  trait SetupSuccessfulTotpVerification extends Setup {
    when(connector.verifyMfa(mockEq(email), mockEq(totpCode))(any[HeaderCarrier])).thenReturn(true)
    val result = service.enableMfa(email, totpCode)(new HeaderCarrier())
  }

  "enableMfa" should {
    "return failed totp when totp verification fails" in new SetupFailedTotpVerification {
      result.totpVerified shouldBe false
    }

    "does not call enable mfa when totp verification fails" in new SetupFailedTotpVerification {
      verify(connector, never).enableMfa(email)(any[HeaderCarrier])
    }

    "return successful totp when totp verification passes" in new SetupSuccessfulTotpVerification {
      result.totpVerified shouldBe true
    }

    "enable MFA totp when totp verification passes" in new SetupSuccessfulTotpVerification {
      verify(connector, times(1)).enableMfa(mockEq(email))(any[HeaderCarrier])
    }

  }
}
