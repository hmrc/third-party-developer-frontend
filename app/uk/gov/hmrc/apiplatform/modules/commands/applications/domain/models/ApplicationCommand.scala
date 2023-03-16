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

import java.time.LocalDateTime

import play.api.libs.json.Json
import uk.gov.hmrc.play.json.Union

import uk.gov.hmrc.apiplatform.modules.applications.domain.models.Collaborator
import uk.gov.hmrc.apiplatform.modules.common.domain.models.Actor

sealed trait ApplicationCommand {
  def actor: Actor
  def timestamp: LocalDateTime
}

case class AddCollaborator(actor: Actor, collaborator: Collaborator, timestamp: LocalDateTime)    extends ApplicationCommand
case class RemoveCollaborator(actor: Actor, collaborator: Collaborator, timestamp: LocalDateTime) extends ApplicationCommand

trait ApplicationCommandFormatters {
  implicit val addCollaboratorFormatter    = Json.format[AddCollaborator]
  implicit val removeCollaboratorFormatter = Json.format[RemoveCollaborator]

  implicit val applicationCommandsFormatter = Union.from[ApplicationCommand]("updateType")
    .and[AddCollaborator]("addCollaborator")
    .and[RemoveCollaborator]("removeCollaborator")
    .format
}

object ApplicationCommandFormatters extends ApplicationCommandFormatters
