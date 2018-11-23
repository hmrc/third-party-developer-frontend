package unit.qr

import qr.OTPAuthURI
import uk.gov.hmrc.play.test.UnitSpec

class OTPAuthURISpec extends UnitSpec {

  "apply" should {
    "generate otpauth uri" in {
      OTPAuthURI("ABC123", "Issuer", "User").toString shouldBe "otpauth://totp/Issuer:User?secret=ABC123&issuer=Issuer"
    }
  }
}
