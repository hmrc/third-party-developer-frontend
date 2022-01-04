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

package modules.submissions.controllers

import play.api.mvc.PathBindable
import modules.submissions.domain.models.QuestionId
import modules.submissions.domain.models.SubmissionId

package object binders {
  implicit def questionIdPathBinder(implicit textBinder: PathBindable[String]): PathBindable[QuestionId] = new PathBindable[QuestionId] {
    override def bind(key: String, value: String): Either[String, QuestionId] = {
      textBinder.bind(key, value).map(QuestionId(_))
    }

    override def unbind(key: String, questionId: QuestionId): String = {
      questionId.value
    }
  }

  implicit def submissionIdPathBinder(implicit textBinder: PathBindable[String]): PathBindable[SubmissionId] = new PathBindable[SubmissionId] {
    override def bind(key: String, value: String): Either[String, SubmissionId] = {
      textBinder.bind(key, value).map(SubmissionId(_))
    }

    override def unbind(key: String, submissionId: SubmissionId): String = {
      submissionId.value
    }
  }
}
