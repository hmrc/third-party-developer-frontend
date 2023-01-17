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

package uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.subscriptions

import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.HmrcSpec

class FieldSpec extends HmrcSpec {

  "FieldName" should {
    "allow creation of random strings" in {
      val fn1 = FieldName.random
      val fn2 = FieldName.random
      fn1 should not equal fn2

      fn1.value.length should be > 0
      fn2.value.length should be > 0
    }

    "sort correctly" in {
      val alice = FieldName("Alice")
      val bob   = FieldName("Bob")

      List(bob, alice).sorted should contain inOrder (alice, bob)
    }
  }

  "FieldValue" should {
    "allow creation of random strings" in {
      val fv1 = FieldValue.random
      val fv2 = FieldValue.random
      fv1 should not equal fv2

      fv1.value.length should be > 0
      fv2.value.length should be > 0
    }
  }
}
