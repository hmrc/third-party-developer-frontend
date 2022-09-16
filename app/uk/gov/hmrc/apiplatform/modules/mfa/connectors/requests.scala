package uk.gov.hmrc.apiplatform.modules.mfa.connectors

import play.api.libs.json.Json

case class ChangeMfaNameRequest(name: String)

object ChangeMfaNameRequest {
  implicit val format = Json.format[ChangeMfaNameRequest]
}
