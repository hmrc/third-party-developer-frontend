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

import connectors.PayloadEncryption
import play.api.libs.json.{JsString, JsValue, Json}
import uk.gov.hmrc.crypto.{AesCrypto, CompositeSymmetricCrypto, Decrypter, Encrypter}
import uk.gov.hmrc.play.test.UnitSpec


class PayloadEncryptionSpec extends UnitSpec {



  "payload encryption" should {
    val name: String = "firstname"
    val lastName: String = "lastname"

    val form = TestForm(name, lastName)
    val encryptedForm = JsString("Vmc5z8wslOsGpRiY85wy04AYu1U2IoRRyviDrU+07egsjQll4TmeV/Xp99uFJ6wg")

    "encrypt a payload" in {
      val payload: JsValue = TestPayloadEncryption.encrypt(form)(Json.format[TestForm])
      payload shouldBe encryptedForm
    }

    "decrypt a payload" in {
      val result: TestForm = TestPayloadEncryption.decrypt(encryptedForm)(Json.format[TestForm])
      result shouldBe form
    }
  }

  object TestPayloadEncryption extends PayloadEncryption {
    override implicit val crypto: CompositeSymmetricCrypto = TestCrypto
  }

  case class TestForm(name: String, lastname: String)

  object TestCrypto extends CompositeSymmetricCrypto {
    override protected val currentCrypto: Encrypter with Decrypter = new AesCrypto {
      override protected val encryptionKey: String = "abcdefghijklmnopqrstuv=="
    }
    override protected val previousCryptos: Seq[Decrypter] = Seq.empty
  }
}
