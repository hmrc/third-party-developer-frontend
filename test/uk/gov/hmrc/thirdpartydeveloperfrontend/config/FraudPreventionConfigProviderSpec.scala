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

package uk.gov.hmrc.thirdpartydeveloperfrontend.config

import play.api.Configuration

import uk.gov.hmrc.apiplatform.modules.apis.domain.models.ServiceName
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.BaseControllerSpec

class FraudPreventionConfigProviderSpec extends BaseControllerSpec {

  "FraudPreventionConfigProvider" should {

    "throw exception when there is no app config setting present" in {

      val testConfig = Configuration.from(Map("fraudPreventionLink.enabled" -> "testval"))

      intercept[RuntimeException] {
        new FraudPreventionConfigProvider(testConfig).get().enabled
      }.getMessage contains "No configuration setting found for key 'applicationCheck"

    }

    "be false when fraudPreventionLink.linkEnabled is false" in {
      val fraudPreventionApis = List("vat-api")
      val uri                 = "uri"
      val testConfig          =
        Configuration.from(Map("fraudPreventionLink.enabled" -> "false", "fraudPreventionLink.apisWithFraudPrevention" -> fraudPreventionApis, "fraudPreventionLink.uri" -> uri))

      val underTest = new FraudPreventionConfigProvider(testConfig).get()

      underTest.enabled shouldBe false
      underTest.apisWithFraudPrevention shouldBe fraudPreventionApis.map(ServiceName(_))
      underTest.uri shouldBe uri
    }

  }
}
