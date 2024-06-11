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

package uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models

import play.api.mvc.Request
import play.api.test.FakeRequest
import play.api.test.Helpers.GET

import uk.gov.hmrc.apiplatform.modules.common.utils.HmrcSpec
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ApplicationConfig

class TermsOfUseVersionSpec extends HmrcSpec {
  "fromVersionString" should {
    "return OLD_VERSION for 1.0 version" in {
      TermsOfUseVersion.fromVersionString("1.0") shouldBe Some(TermsOfUseVersion.OLD_JOURNEY)
    }
    "return OLD_VERSION for 1.1 version" in {
      TermsOfUseVersion.fromVersionString("1.1") shouldBe Some(TermsOfUseVersion.OLD_JOURNEY)
    }
    "return OLD_VERSION for 1.2 version" in {
      TermsOfUseVersion.fromVersionString("1.2") shouldBe Some(TermsOfUseVersion.OLD_JOURNEY)
    }
    "return NEW_JOURNEY for 2.0 version" in {
      TermsOfUseVersion.fromVersionString("2.0") shouldBe Some(TermsOfUseVersion.NEW_JOURNEY)
    }
  }

  "latest" should {
    "return NEW_JOURNEY" in {
      TermsOfUseVersion.latest shouldBe TermsOfUseVersion.NEW_JOURNEY
    }
  }

  "getTermsOfUseAsHtml" should {
    "return old content for OLD_JOURNEY" in {
      TermsOfUseVersion.OLD_JOURNEY.getTermsOfUseAsHtml()(mock[ApplicationConfig], mock[Request[Any]]).toString() should include(
        "These terms of use explain what you can expect from us and what we expect from you"
      )
    }
    "return new content for NEW_JOURNEY" in {
      TermsOfUseVersion.NEW_JOURNEY.getTermsOfUseAsHtml()(
        mock[ApplicationConfig],
        FakeRequest(GET, "/")
      ).toString() should not include "These terms of use explain what you can expect from us and what we expect from you"
    }
  }

  "toString" should {
    "return correct value for OLD_JOURNEY" in {
      TermsOfUseVersion.OLD_JOURNEY.toString() shouldBe "1.2"
    }
    "return correct value for NEW_JOURNEY" in {
      TermsOfUseVersion.NEW_JOURNEY.toString() shouldBe "2.0"
    }
  }
}
