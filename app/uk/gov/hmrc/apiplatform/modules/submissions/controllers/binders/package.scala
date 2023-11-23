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

package uk.gov.hmrc.apiplatform.modules.submissions.controllers

import play.api.mvc.PathBindable

import uk.gov.hmrc.apiplatform.modules.submissions.domain.models.{Question}
import uk.gov.hmrc.apiplatform.modules.applications.submissions.domain.models.SubmissionId

package object binders {

  implicit def questionIdPathBinder(implicit textBinder: PathBindable[String]): PathBindable[Question.Id] = new PathBindable[Question.Id] {

    override def bind(key: String, value: String): Either[String, Question.Id] = {
      textBinder.bind(key, value).map(Question.Id(_))
    }

    override def unbind(key: String, questionId: Question.Id): String = {
      questionId.value
    }
  }

  implicit def submissionIdPathBinder(implicit textBinder: PathBindable[String]): PathBindable[SubmissionId] = new PathBindable[SubmissionId] {

    override def bind(key: String, value: String): Either[String, SubmissionId] = {
      // for {
      //   text <- textBinder.bind(key, value)
      //   env  <- Environment.apply(text).toRight("Not a valid environment")
      // } yield env

      textBinder.bind(key, value).map(SubmissionId.apply(_))
    }

    override def unbind(key: String, submissionId: SubmissionId): String = {
      submissionId.toString()
    }
  }
}
