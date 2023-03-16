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

import play.api.libs.json.Json
import uk.gov.hmrc.play.json.Union

sealed trait CommandFailure

object CommandFailures {
  case object ApplicationNotFound             extends CommandFailure
  case object CannotRemoveLastAdmin           extends CommandFailure
  case object ActorIsNotACollaboratorOnApp    extends CommandFailure
  case object CollaboratorDoesNotExistOnApp   extends CommandFailure
  case object CollaboratorHasMismatchOnApp    extends CommandFailure
  case object CollaboratorAlreadyExistsOnApp  extends CommandFailure
  case class GenericFailure(describe: String) extends CommandFailure
}

trait CommandFailureJsonFormatters {
  import CommandFailures._

  implicit val formatApplicationNotFound            = Json.format[ApplicationNotFound.type]
  implicit val formatCannotRemoveLastAdmin          = Json.format[CannotRemoveLastAdmin.type]
  implicit val formatActorIsNotACollaboratorOnApp   = Json.format[ActorIsNotACollaboratorOnApp.type]
  implicit val formatCollaboratorDoesNotExistOnApp  = Json.format[CollaboratorDoesNotExistOnApp.type]
  implicit val formatCollaboratorHasMismatchOnApp   = Json.format[CollaboratorHasMismatchOnApp.type]
  implicit val formatCollaboratorAlreadyExistsOnApp = Json.format[CollaboratorAlreadyExistsOnApp.type]
  implicit val formatGenericFailure                 = Json.format[GenericFailure]

  implicit val formatCommandFailures = Union.from[CommandFailure]("failureType")
    .and[ApplicationNotFound.type]("ApplicationNotFound")
    .and[CannotRemoveLastAdmin.type]("CannotRemoveLastAdmin")
    .and[ActorIsNotACollaboratorOnApp.type]("ActorIsNotACollaboratorOnApp")
    .and[CollaboratorDoesNotExistOnApp.type]("CollaboratorDoesNotExistOnApp")
    .and[CollaboratorHasMismatchOnApp.type]("CollaboratorHasMismatchOnApp")
    .and[CollaboratorAlreadyExistsOnApp.type]("CollaboratorAlreadyExistsOnApp")
    .and[GenericFailure]("GenericFailure")
    .format
}

object CommandFailureJsonFormatters extends CommandFailureJsonFormatters
