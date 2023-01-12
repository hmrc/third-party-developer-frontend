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

// scalastyle:off illegal.imports
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.Base64
import javax.imageio.ImageIO
import scala.collection.JavaConverters._

import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.{BarcodeFormat, EncodeHintType}
//scalastyle:on illegal.imports

case class QRCode(scale: Int = 1) {
  private val pixels = Array.fill(scale)(QRCode.white)

  private def generateImage(text: String): Array[Byte] = {
    val qrCodeWriter = new QRCodeWriter()
    val byteMatrix   = qrCodeWriter.encode(text, BarcodeFormat.QR_CODE, 0, 0, QRCode.defaultHintMap)
    val width        = byteMatrix.getWidth
    val imageSize    = width * scale

    val image = new BufferedImage(imageSize, imageSize, BufferedImage.TYPE_INT_RGB)

    for (i <- 0 until width)
      for (j <- 0 until width)
        if (!byteMatrix.get(i, j)) {
          image.setRGB(i * scale, j * scale, scale, scale, pixels, 0, 0)
        }

    val byteArrayOutputStream = new ByteArrayOutputStream()
    ImageIO.write(image, QRCode.pngImageFormat, byteArrayOutputStream)
    byteArrayOutputStream.flush()
    val imageBytes            = byteArrayOutputStream.toByteArray
    byteArrayOutputStream.close()
    imageBytes
  }

  def generateDataImageBase64(text: String) = {
    val imageAsBase64 = Base64.getEncoder.encodeToString(generateImage(text))
    s"data:image/png;base64,$imageAsBase64"
  }
}

object QRCode {
  private val white: Int     = 0xffffff
  private val pngImageFormat = "png"

  private val defaultHintMap = Map[EncodeHintType, Any](
    EncodeHintType.CHARACTER_SET    -> StandardCharsets.UTF_8.name(),
    EncodeHintType.MARGIN           -> 1,
    EncodeHintType.ERROR_CORRECTION -> ErrorCorrectionLevel.L
  ).asJava
}
