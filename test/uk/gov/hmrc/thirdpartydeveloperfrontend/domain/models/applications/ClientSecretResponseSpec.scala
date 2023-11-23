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

package uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications

import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.{ClientSecret, ClientSecretResponse}
import uk.gov.hmrc.apiplatform.modules.common.utils.{FixedClock, JsonFormattersSpec}

class ClientSecretResponseSpec extends JsonFormattersSpec with FixedClock {
  val anId             = ClientSecret.Id.random
  val fakeHashedSecret = "blahblahblah"
  val aClientSecret    = ClientSecretResponse(anId, "bob", now(), None)

  "ClientSecretResponse" should {
    val expectedJsonText = s"""{"name":"bob","createdOn":"$nowAsText","id":"${anId.value}"}"""

    "convert to json" in {
      testToJson(aClientSecret)(
        "name"      -> "bob",
        "createdOn" -> s"$nowAsText",
        "id"        -> s"${anId.value}"
      )
    }

    "read from json" in {
      testFromJson[ClientSecretResponse](expectedJsonText)(aClientSecret)
    }
  }
}
