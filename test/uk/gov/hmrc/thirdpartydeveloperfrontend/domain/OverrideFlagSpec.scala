/*
 * Copyright 2022 HM Revenue & Customs
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

package uk.gov.hmrc.thirdpartydeveloperfrontend.domain

import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.OverrideFlag
import play.api.libs.json.{JsError, Json, JsString}
import utils.AsyncHmrcSpec

class OverrideFlagSpec extends AsyncHmrcSpec {

  "OverrideFlag" should {
    "parse from the new format" in {
      val overrideFlag = Json.fromJson[OverrideFlag](Json.parse("""{ "overrideType": "OVERRIDE_FLAG" }"""))
      overrideFlag.asOpt shouldEqual Some(OverrideFlag("OVERRIDE_FLAG"))
    }

    "parser from the old format -> the new format" in {
      val overrideFlag = Json.fromJson[OverrideFlag](JsString("OVERRIDE_FLAG"))
      overrideFlag.asOpt shouldEqual Some(OverrideFlag("OVERRIDE_FLAG"))
    }

    "parser should fail on nonesense" in {
      Json.fromJson[OverrideFlag](Json.parse("""{ "notEvenClose": "OverrideFlag" }""")) shouldBe an[JsError]
      Json.fromJson[OverrideFlag](Json.parse("""["OverrideFlag"]""")) shouldBe an[JsError]
    }
  }
}
