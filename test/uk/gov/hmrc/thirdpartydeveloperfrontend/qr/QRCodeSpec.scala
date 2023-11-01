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

package uk.gov.hmrc.thirdpartydeveloperfrontend.qr

import java.io.ByteArrayInputStream
import java.util.Base64
import javax.imageio.ImageIO

import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.AsyncHmrcSpec

class QRCodeSpec extends AsyncHmrcSpec {

  /* The Java 8 and Java 9 ImageIO libraries use different compression levels when writing PNG files.
   * See the description in https://github.com/gredler/jdk9-png-writer-backport
   *
   * To avoid this breaking tests in future, this test now checks the decoded image RGBA data instead.
   * The data generated during the test is decoded and compared with test PNG files in the resources.
   *
   * For reference, the Base64 encoding of the QR image for "Test" is:
   * Java 8:  iVBORw0KGgoAAAANSUhEUgAAABcAAAAXCAIAAABvSEP3AAAAnklEQVR42q2TUQ7AIAhDuf+lt+wLLY+qcSYzmkHBtsTzx4rvo5UREeOOkRkxYc+ZskNkrYP1BV0jzT/T3QEKvH+JgpWR6ZYXZr7hiDVqXRBx7JcxTXQZA+ScKL55Y5kJpVrW+IW523cKQoDShosqvPbiNcLnHMw0HlhTw4VQa66LaTSkqF/8NErlWmarFzPiuzONdADNy5nuMlWj+/UCt7Jrv4IzAM8AAAAASUVORK5CYII=
   * Java 11: iVBORw0KGgoAAAANSUhEUgAAABcAAAAXCAIAAABvSEP3AAAAu0lEQVR4Xq2OgQ7EIAxC/f+fvourUlrQXC4jman0wRyfNzTm55TEM+NUZQsyobU7nKzeUrrl/yWj5GXHKhkhr7sq9U0LS2n2WaVFlYR7BWu1nBTEL8o/xLDc7WAG0OZsCUtPDrSTU3PgQLpSB5+VDO/gsuA3wZwDuDYYlEzA6wzLirkYWhHkXzvcK2LQa7aszq1c78D92v+AuXO0NddLC3MWSLJlPFQBYKaFhRiujQFQWlRAT0kG+iv+0xe3smu/cFffegAAAABJRU5ErkJggg==
   *
   * These can be viewed in a browser by using the following HTML (see QRCodeView.scala.html):
   * <img id="qrCode" alt="QR Code" src="data:image/png;base64,encoded-string-here">
   */
  def testQrCodeImages(actualInImageBase64: String, expectedImageFileName: String) = {
    val actualImageData   = ImageIO.read(new ByteArrayInputStream(Base64.getDecoder.decode(actualInImageBase64)))
      .getData.getDataBuffer
    val expectedImageData = ImageIO.read(getClass.getResourceAsStream(expectedImageFileName))
      .getData.getDataBuffer

    val actualSize   = actualImageData.getSize
    val expectedSize = expectedImageData.getSize
    withClue("Image sizes do not match:") { actualSize shouldBe expectedSize }

    val actualContents   = List.tabulate(actualSize)(actualImageData.getElem)
    val expectedContents = List.tabulate(expectedSize)(expectedImageData.getElem)
    withClue("Image contents do not match:") { actualContents shouldBe expectedContents }
  }

  "generateDataImageBase64" should {
    "generate a base64 encoded image of a QR code" in {
      val httpImgData = QRCode().generateDataImageBase64("Test")

      val prefix :: imageInBase64 :: Nil = httpImgData.split(",", 2).toList
      prefix shouldBe "data:image/png;base64"
      testQrCodeImages(imageInBase64, "/qrCodeImages/Small_QR_Code.png")
    }

    "generate a base64 encoded image of a barcode from the supplied text with the specified scale" in {
      val httpImgData = QRCode(5).generateDataImageBase64("QRCode test text")

      val prefix :: imageInBase64 :: Nil = httpImgData.split(",", 2).toList
      prefix shouldBe "data:image/png;base64"
      testQrCodeImages(imageInBase64, "/qrCodeImages/Large_QR_Code.png")
    }
  }
}
