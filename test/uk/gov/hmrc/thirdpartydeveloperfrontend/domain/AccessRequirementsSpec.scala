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

package uk.gov.hmrc.thirdpartydeveloperfrontend.domain

import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.subscriptions.{AccessRequirements, DevhubAccessLevel, DevhubAccessRequirement, DevhubAccessRequirements}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.subscriptions.DevhubAccessLevel._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.subscriptions.DevhubAccessRequirement._
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

class AccessRequirementsSpec extends AnyWordSpec with Matchers {

  "DevhubRequirement" should {

    "Developer" should {
      "satisfy the Anyone requirement" in {
        DevhubAccessLevel.Developer.satisfiesRequirement(Anyone) shouldBe true
      }
      "not satisfy the AdminOnly requirement" in {
        DevhubAccessLevel.Developer.satisfiesRequirement(AdminOnly) shouldBe false
      }
      "not satisfy the NoOne requirement" in {
        DevhubAccessLevel.Developer.satisfiesRequirement(NoOne) shouldBe false
      }
    }

    "Admin" should {
      "satisfy the Anyone requirement" in {
        Admininstator.satisfiesRequirement(Anyone) shouldBe true
      }
      "satisfy the AdminOnly requirement" in {
        Admininstator.satisfiesRequirement(AdminOnly) shouldBe true
      }
      "not satisfy the NoOne requirement" in {
        Admininstator.satisfiesRequirement(NoOne) shouldBe false
      }
    }
  }

  "DevhubAccessRequirements" should {

    "a producer team wants to restrict collaborators to only view but not change a field" in {
      val dar = DevhubAccessRequirements(read = DevhubAccessRequirement.Default, write = NoOne)

      dar.satisfiesRead(DevhubAccessLevel.Developer) shouldBe true
      dar.satisfiesRead(Admininstator) shouldBe true

      dar.satisfiesWrite(DevhubAccessLevel.Developer) shouldBe false
      dar.satisfiesWrite(Admininstator) shouldBe false
    }

    "a producer team wants to restrict Developers from even viewing a field but allow administrators to read and write" in {
      val dar = DevhubAccessRequirements(read = AdminOnly, write = AdminOnly)

      dar.satisfiesRead(DevhubAccessLevel.Developer) shouldBe false
      dar.satisfiesWrite(DevhubAccessLevel.Developer) shouldBe false

      dar.satisfiesRead(Admininstator) shouldBe true
      dar.satisfiesWrite(Admininstator) shouldBe true
    }

    "a producer team wants to restrict Developers to viewing a field but allow administrators to read and write" in {
      val dar = DevhubAccessRequirements(read = Anyone, write = AdminOnly)

      dar.satisfiesRead(DevhubAccessLevel.Developer) shouldBe true
      dar.satisfiesWrite(DevhubAccessLevel.Developer) shouldBe false

      dar.satisfiesRead(Admininstator) shouldBe true
      dar.satisfiesWrite(Admininstator) shouldBe true
    }

    "a producer team wants to allow anyone to view and change a field" in {
      val dar = DevhubAccessRequirements(read = Anyone)

      dar.satisfiesRead(DevhubAccessLevel.Developer) shouldBe true
      dar.satisfiesWrite(DevhubAccessLevel.Developer) shouldBe true

      dar.satisfiesRead(Admininstator) shouldBe true
      dar.satisfiesWrite(Admininstator) shouldBe true
    }
  }

  "Stuff" in {
    val access = AccessRequirements(devhub = DevhubAccessRequirements(NoOne, NoOne))

    access.devhub.satisfiesRead(DevhubAccessLevel.Developer) shouldBe false
    access.devhub.satisfiesWrite(DevhubAccessLevel.Developer) shouldBe false

    access.devhub.satisfiesRead(Admininstator) shouldBe false
    access.devhub.satisfiesWrite(Admininstator) shouldBe false
  }
}
