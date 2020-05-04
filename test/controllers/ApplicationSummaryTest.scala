/*
 * Copyright 2020 HM Revenue & Customs
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

package controllers

import domain.Role.DEVELOPER
import domain.State.TESTING
import domain.{AccessType, TermsOfUseStatus}
import org.joda.time.DateTime
import org.scalacheck.Prop.False
import org.scalatest.{Matchers, WordSpec}

class ApplicationSummaryTest extends WordSpec with Matchers {

  "noProductionApplications" should {
    val sandboxApp = ApplicationSummary("", "", "Sandbox", DEVELOPER, TermsOfUseStatus.AGREED, TESTING, new DateTime(), new DateTime(), AccessType.STANDARD )
    val productionApp = ApplicationSummary("", "", "Production", DEVELOPER, TermsOfUseStatus.AGREED, TESTING, new DateTime(), new DateTime(), AccessType.STANDARD)

    "return true if only sandbox apps" in {
      val apps = Seq(sandboxApp)

      ApplicationSummary.noProductionApplications(apps) shouldBe true
    }

    "return false if there is a production app" in {
      val apps = Seq(productionApp, sandboxApp)

      ApplicationSummary.noProductionApplications(apps) shouldBe false
    }
  }
}
