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

package uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models

import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.emailpreferences.TaxRegimeInterests
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

class TaxRegimeInterestsSpec extends AnyWordSpec with Matchers {
  val regime = "A Regime"

  "tax regime interests add works" in {
    val tri = TaxRegimeInterests(regime, Set("a","b","c"))

    tri.addService("d").services should contain allOf("a","b","c","d")
  }
}
