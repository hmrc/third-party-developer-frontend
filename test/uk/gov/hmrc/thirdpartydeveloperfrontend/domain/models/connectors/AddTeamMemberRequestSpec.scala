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

package uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors

import play.api.libs.json._

import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.Collaborator
import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress
import uk.gov.hmrc.apiplatform.modules.common.utils.JsonFormattersSpec
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.HmrcSpec

class AddTeamMemberRequestSpec extends HmrcSpec with JsonFormattersSpec {

  "AddTeamMemberRequest" should {
    val request = AddTeamMemberRequest(LaxEmailAddress("bob@example.com"), Collaborator.Roles.DEVELOPER, None)

    "write role" in {
      val role: Collaborator.Role = Collaborator.Roles.DEVELOPER
      Json.toJson(role) shouldBe JsString("DEVELOPER")

    }

    "read role" in {
      Json.fromJson[Collaborator.Role](JsString("ADMINISTRATOR")) shouldBe JsSuccess(Collaborator.Roles.ADMINISTRATOR)
    }

    "write to json" in {
      testToJson(request)("email" -> "bob@example.com", "role" -> "DEVELOPER")
    }

    "read from json" in {
      testFromJson[AddTeamMemberRequest]("""{"email": "bob@example.com", "role": "DEVELOPER"}""")(request)
    }
  }
}
