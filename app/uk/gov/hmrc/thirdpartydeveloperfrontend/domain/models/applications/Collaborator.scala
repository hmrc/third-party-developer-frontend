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

package uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications

import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.UserId

case class Collaborator(emailAddress: String, role: CollaboratorRole, userId: UserId)

object Collaborator {
  import play.api.libs.json.Json

  implicit val format = Json.format[Collaborator]
}

case class AddCollaborator(emailAddress: String, role: CollaboratorRole)

object AddCollaborator {
  import play.api.libs.json.Json

  implicit val format = Json.format[AddCollaborator]
}