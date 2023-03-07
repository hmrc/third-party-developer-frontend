package uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors

import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.HmrcSpec
import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress
import uk.gov.hmrc.apiplatform.modules.applications.domain.models.Collaborator
import uk.gov.hmrc.apiplatform.modules.common.utils.JsonFormattersSpec
import play.api.libs.json._

class AddTeamMemberRequestSpec extends HmrcSpec with JsonFormattersSpec {
  
  "AddTeamMemberRequest" should {
    val request = AddTeamMemberRequest(LaxEmailAddress("bob@example.com"), Collaborator.Roles.DEVELOPER, None)

    "write role" in {
      val role: Collaborator.Role = Collaborator.Roles.DEVELOPER
      Json.toJson(role) shouldBe JsString("DEVELOPER")

    }
  
    "read role" in {
      val role: Collaborator.Role = Collaborator.Roles.DEVELOPER
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
