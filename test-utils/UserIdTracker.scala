/*
 * Copyright 2021 HM Revenue & Customs
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

package utils

import scala.collection.mutable
import domain.models.developers.UserId
import domain.models.applications.{Collaborator, CollaboratorRole}
import domain.models.applications.CollaboratorRole._

trait UserIdTracker {
  def idOf(email: String): UserId
}
trait LocalUserIdTracker extends UserIdTracker {
  private lazy val idsByEmail = mutable.Map[String, UserId]()

  def idOf(email: String): UserId = idsByEmail.getOrElseUpdate(email, UserId.random)
}

trait CollaboratorTracker { 
  self : UserIdTracker =>
  
  def collaboratorOf(email: String, role: CollaboratorRole): Collaborator = Collaborator(email, role, idOf(email))

  implicit class CollaboratorSyntax(value: String) {
    def asAdministratorCollaborator = collaboratorOf(value, ADMINISTRATOR)
    def asDeveloperCollaborator = collaboratorOf(value, DEVELOPER)
    def asRole(role:CollaboratorRole) = collaboratorOf(value, role)
  }
}

// Use this when you want to share the map across files like component tests
object GlobalUserIdTracker extends LocalUserIdTracker
