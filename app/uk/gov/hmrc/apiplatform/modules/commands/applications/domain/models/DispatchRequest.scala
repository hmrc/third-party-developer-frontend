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

package uk.gov.hmrc.apiplatform.modules.commands.applications.domain.models

import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress
case class DispatchRequest(command: ApplicationCommand, verifiedCollaboratorsToNotify: Set[LaxEmailAddress])

object DispatchRequest {
  import play.api.libs.json._

  val readsExactDispatchRequest: Reads[DispatchRequest] = Json.reads[DispatchRequest]
  val readsExactCommand: Reads[DispatchRequest]         = ApplicationCommand.formatter.map(cmd => DispatchRequest(cmd, Set.empty))

  implicit val readsDispatchRequest: Reads[DispatchRequest] = readsExactDispatchRequest orElse readsExactCommand

  implicit val writesDispatchRequest: Writes[DispatchRequest] = Json.writes[DispatchRequest]
  implicit val formatDispatchRequest                          = Format(readsDispatchRequest, writesDispatchRequest)
}
