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

package unit.helpers

import helpers.PluralizeHelper
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import uk.gov.hmrc.play.test.UnitSpec

class PluralizeHelperSpec extends UnitSpec with ScalaFutures with MockitoSugar {
  "1" should {
    "be singular" in {
        PluralizeHelper.pluralize(1, "cat" , "cats") shouldBe "cat"
    }
  }

  "2" should {
    "be plural" in {
      PluralizeHelper.pluralize(2, "cat" , "cats") shouldBe "cats"
    }
  }
}
