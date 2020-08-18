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

package utils

import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen

import scala.util.Random

object Generators {
  def shuffle[T](xs: Seq[T]): Gen[Seq[T]] =
    arbitrary[Int].map(new Random(_).shuffle(xs))

  val asciiLower = Gen.alphaLowerChar
  val asciiUpper = Gen.alphaUpperChar
  val asciiDigit = Gen.numChar
  val asciiSpecial = Gen.oneOf(" !\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~")
  val asciiPrintable = Gen.oneOf(asciiLower, asciiUpper, asciiDigit, asciiSpecial)
}
