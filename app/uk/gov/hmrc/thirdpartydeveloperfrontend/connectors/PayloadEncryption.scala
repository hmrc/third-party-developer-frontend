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

package uk.gov.hmrc.thirdpartydeveloperfrontend.connectors

import javax.inject.{Inject, Singleton}
import scala.concurrent.Future

import play.api.libs.json._
import uk.gov.hmrc.crypto.json.JsonEncryption
import uk.gov.hmrc.crypto.{Decrypter, Encrypter, _}

import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ApplicationConfig

case class SensitiveT[T](override val decryptedValue: T) extends Sensitive[T]

class PayloadEncryption @Inject() (implicit val crypto: LocalCrypto) {

  def encrypt[T](payload: T)(implicit writes: Writes[T]): JsValue = {
    val encrypter = JsonEncryption.sensitiveEncrypter[T, SensitiveT[T]]
    encrypter.writes(SensitiveT(payload))
  }

  def decrypt[T](payload: JsString)(implicit reads: Reads[T]): T = {
    val encryptedValue: JsValue = payload
    val decrypter               = JsonEncryption.sensitiveDecrypter[T, SensitiveT[T]](SensitiveT.apply)
    decrypter.reads(encryptedValue)
      .asOpt
      .map(_.decryptedValue)
      .getOrElse { sys.error(s"Failed to decrypt payload: [$payload]") }
  }
}

@Singleton
class LocalCrypto @Inject() (applicationConfig: ApplicationConfig) extends Encrypter with Decrypter {
  implicit val aesCrypto: Encrypter with Decrypter = SymmetricCryptoFactory.aesCrypto(applicationConfig.jsonEncryptionKey)

  override def encrypt(plain: PlainContent): Crypted = aesCrypto.encrypt(plain)

  override def decryptAsBytes(reversiblyEncrypted: Crypted): PlainBytes = aesCrypto.decryptAsBytes(reversiblyEncrypted)

  override def decrypt(reversiblyEncrypted: Crypted): PlainText = aesCrypto.decrypt(reversiblyEncrypted)
}

case class SecretRequest(data: String)

object SecretRequest {
  implicit val format: Format[SecretRequest] = Json.format[SecretRequest]
}

class EncryptedJson @Inject() (payloadEncryption: PayloadEncryption) {

  def secretRequestJson[R](payload: JsValue, block: JsValue => Future[R]) = {
    block(toSecretRequestJson(payload))
  }

  def toSecretRequestJson[T](payload: T)(implicit writes: Writes[T]): JsValue = {
    Json.toJson(SecretRequest(payloadEncryption.encrypt(payload).as[String]))
  }

  def secretRequest[I, R](input: I, block: SecretRequest => Future[R])(implicit w: Writes[I]) = {
    block(toSecretRequest(w.writes(input)))
  }

  def toSecretRequest[T](payload: T)(implicit writes: Writes[T]): SecretRequest = {
    SecretRequest(payloadEncryption.encrypt(payload).as[String])
  }
}
