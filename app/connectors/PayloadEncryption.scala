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

package connectors

import config.ApplicationConfig
import play.api.libs.json._
import uk.gov.hmrc.crypto._
import uk.gov.hmrc.crypto.json.{JsonDecryptor, JsonEncryptor}

import scala.concurrent.Future

trait PayloadEncryption {

  implicit val crypto: CompositeSymmetricCrypto

  def encrypt[T](payload: T)(implicit writes: Writes[T]): JsValue = {
    val encryptor = new JsonEncryptor[T]()(crypto, writes)
    Json.toJson(encryptor.writes(Protected(payload)))
  }

  def decrypt[T](payload: JsValue)(implicit reads: Reads[T]): T = {
    val decryptor = new JsonDecryptor()(crypto, reads)
    val decrypted: JsResult[Protected[T]] = decryptor.reads(payload)

    decrypted.asOpt.map(_.decryptedValue).getOrElse(throw new scala.RuntimeException(s"Failed to decrypt payload: [$payload]"))
  }
}

object PayloadEncryption extends PayloadEncryption {
  override implicit val crypto = LocalCrypto
}

object LocalCrypto extends CompositeSymmetricCrypto {
  override protected val currentCrypto: Encrypter with Decrypter = new AesCrypto {
    override protected val encryptionKey: String = ApplicationConfig.jsonEncryptionKey
  }
  override protected val previousCryptos: Seq[Decrypter] = Seq.empty
}


case class SecretRequest(data: String)

object SecretRequest {
  implicit val format = Json.format[SecretRequest]
}

trait EncryptedJson {
  val payloadEncryption: PayloadEncryption


  def secretRequestJson[R](payload: JsValue, block: JsValue => Future[R]) = {
    block(toSecretRequestJson(payload))
  }

  def toSecretRequestJson[T](payload: T)(implicit writes: Writes[T]): JsValue = {
    Json.toJson(SecretRequest(payloadEncryption.encrypt(payload).as[String]))
  }

}
