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

import modules.submissions.domain.models.{QuestionId, QuestionItem}
import cats.data.NonEmptyList

trait AsIdsHelpers {
  implicit class ListQIdSyntax(questionItems: List[QuestionItem]) {
    def asIds(): List[QuestionId] = {
      questionItems.map(_.question.id)
    }
  }

  implicit class NELQIdSyntax(questionItems: NonEmptyList[QuestionItem]) {
    def asIds(): List[QuestionId] = {
      questionItems.toList.map(_.question.id)
    }
  }
}

object AsIdsHelpers extends AsIdsHelpers
