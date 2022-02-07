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

package uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.subscriptions

import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.CollaboratorRole

sealed trait DevhubAccessRequirement
object DevhubAccessRequirement {
  final val Default: DevhubAccessRequirement = Anyone

  case object NoOne extends DevhubAccessRequirement
  case object AdminOnly extends DevhubAccessRequirement
  case object Anyone extends DevhubAccessRequirement
}

case class DevhubAccessRequirements private (val read: DevhubAccessRequirement, val write: DevhubAccessRequirement) {
  def satisfiesRead(dal: DevhubAccessLevel): Boolean = dal.satisfiesRequirement(read) // ReadWrite will be at least as strict.
  def satisfiesWrite(dal: DevhubAccessLevel): Boolean = dal.satisfiesRequirement(write)
}

object DevhubAccessRequirements {
  import DevhubAccessRequirement._

  final val Default = DevhubAccessRequirements(DevhubAccessRequirement.Default, DevhubAccessRequirement.Default)

  // Do not allow greater restrictions on read than on write
  // - it would make no sense to allow NoOne read but everyone write or developer write and admin read
  //
  def apply(read: DevhubAccessRequirement,
            write: DevhubAccessRequirement = DevhubAccessRequirement.Default): DevhubAccessRequirements = (read,write) match {

    case (NoOne, _)          => new DevhubAccessRequirements(NoOne, NoOne){}
    case (AdminOnly, Anyone) => new DevhubAccessRequirements(AdminOnly,AdminOnly){}
    case _                   => new DevhubAccessRequirements(read,write){}
  }
}

case class AccessRequirements(devhub: DevhubAccessRequirements)
object AccessRequirements {
  final val Default = AccessRequirements(devhub = DevhubAccessRequirements.Default)
}

sealed trait DevhubAccessLevel {
  def satisfiesRequirement(requirement: DevhubAccessRequirement): Boolean = DevhubAccessLevel.satisfies(requirement)(this)
}

object DevhubAccessLevel {

  def fromRole(role: CollaboratorRole) : DevhubAccessLevel = role match {
      case CollaboratorRole.ADMINISTRATOR => DevhubAccessLevel.Admininstator
      case CollaboratorRole.DEVELOPER => DevhubAccessLevel.Developer
  }

  case object Developer extends DevhubAccessLevel
  case object Admininstator extends DevhubAccessLevel

  import DevhubAccessRequirement._
  def satisfies(requirement: DevhubAccessRequirement)(actual: DevhubAccessLevel): Boolean = (requirement, actual) match {
    case (NoOne, _) => false
    case (AdminOnly, Developer) => false
    case _ => true
  }
}
