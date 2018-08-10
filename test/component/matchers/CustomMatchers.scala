/*
 * Copyright 2018 HM Revenue & Customs
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

package component.matchers

import org.scalatest.matchers.{MatchResult, Matcher}

object CustomMatchers extends CustomMatchers

trait CustomMatchers {

  class ContainsAllTextsInOrderMatcher(toFind: List[String]) extends Matcher[String] {

    def apply(left: String) = containsAllTextsInOrder(left, toFind)
  }

  def containInOrder(toFind: List[String]) = new ContainsAllTextsInOrderMatcher(toFind)

  private def containsAllTextsInOrder(text: String, toFind: List[String]): MatchResult = toFind match {
    case Nil => MatchResult(matches = true, "", "Found text")
    case x :: xs if x.contains("regex=") => {
      val regex = x.split("=")(1).r
      regex.findFirstMatchIn(text).fold(MatchResult(matches = false, s"Could not find regex '$regex' in:\n$text", "Found text")) { matcher =>
        containsAllTextsInOrder(text.substring(matcher.end), xs)
      }
    }
    case x :: xs if text.contains(x)  => containsAllTextsInOrder(text.substring(text.indexOf(x) + x.length), xs)
    case x :: _ => MatchResult(matches = false, s"Could not find '$x' in:\n$text", "Found text")
  }

}
