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

package unit.domain

import domain.{APIStatus, APISubscriptionStatus, APIVersion}
import uk.gov.hmrc.play.test.UnitSpec

class APISubscriptionStatusTest extends UnitSpec {

  def aSubscription(name: String = "name",
                    service: String = "service",
                    context: String = "context",
                    version: APIVersion = APIVersion("1.0", APIStatus.STABLE),
                    subscribed: Boolean = true,
                    requiresTrust: Boolean = false) = {
    APISubscriptionStatus(name, service, context, version, subscribed, requiresTrust)
  }

  "canUnsubscribe" should {

    "be true if user is an ADMINISTRATOR or DEVELOPER, and the API is not deprecated" in {
      APIStatus.values.filterNot(_ == APIStatus.DEPRECATED) foreach { s =>
        aSubscription(version = APIVersion("2.0", s)).canUnsubscribe shouldBe true
      }
    }

    "be false if the API version is deprecated" in {
      aSubscription(version = APIVersion("2.0", APIStatus.DEPRECATED)).canUnsubscribe shouldBe false
    }
  }
}
