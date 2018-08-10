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

package utils

import connectors.{EncryptedJson, PayloadEncryption}
import uk.gov.hmrc.crypto.{AesCrypto, Decrypter, Encrypter, CompositeSymmetricCrypto}

trait TestPayloadEncryptor {
  // encrypt
  object TestCrypto extends CompositeSymmetricCrypto {
    override protected val currentCrypto: Encrypter with Decrypter = new AesCrypto {
      override protected val encryptionKey: String = "abcdefghijklmnopqrstuv=="
    }
    override protected val previousCryptos: Seq[Decrypter] = Seq.empty
  }

  object TestPayloadEncryption extends PayloadEncryption {
    override implicit val crypto: CompositeSymmetricCrypto = TestCrypto
  }

  object EncryptedJson extends EncryptedJson {
    override val payloadEncryption = TestPayloadEncryption
  }

}
