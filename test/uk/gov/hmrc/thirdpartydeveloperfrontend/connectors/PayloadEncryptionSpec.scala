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

import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ApplicationConfig
import play.api.libs.json.{JsString, JsValue, Json}
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.AsyncHmrcSpec

class PayloadEncryptionSpec extends AsyncHmrcSpec {

  trait Setup {
    val mockAppConfig     = mock[ApplicationConfig]
    when(mockAppConfig.jsonEncryptionKey).thenReturn("czV2OHkvQj9FKEgrTWJQZVNoVm1ZcTN0Nnc5eiRDJkY=")
    val payloadEncryption = new PayloadEncryption(new LocalCrypto(mockAppConfig))
  }

  "payload encryption" should {
    val name: String     = "firstname"
    val lastName: String = "lastname"

    val form          = TestForm(name, lastName)
    val encryptedForm = JsString("K6SteWOyCf6TsdT03f0h/sbCPiYct4CjPS4+5LhGyLKhhnbSu401qZkv0lPIDiPo")

    "encrypt a payload" in new Setup {
      val payload: JsValue = payloadEncryption.encrypt(form)(Json.format[TestForm])
      payload shouldBe encryptedForm
    }

    "decrypt a payload" in new Setup {
      val result: TestForm = payloadEncryption.decrypt(encryptedForm)(Json.format[TestForm])
      result shouldBe form
    }
  }

  case class TestForm(name: String, lastname: String)
}
