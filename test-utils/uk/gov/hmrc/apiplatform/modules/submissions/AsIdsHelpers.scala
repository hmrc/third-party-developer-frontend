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

package uk.gov.hmrc.apiplatform.modules.submissions

import cats.data.NonEmptyList

import uk.gov.hmrc.apiplatform.modules.submissions.domain.models._

trait AsIdsHelpers {

  implicit class ListQuestionIdSyntax(questionItems: List[QuestionItem]) {

    def asIds(): List[Question.Id] = {
      questionItems.map(_.question.id)
    }
  }

  implicit class NELQuestionIdSyntax(questionItems: NonEmptyList[QuestionItem]) {

    def asIds(): List[Question.Id] = {
      questionItems.toList.map(_.question.id)
    }
  }
}

object AsIdsHelpers extends AsIdsHelpers
