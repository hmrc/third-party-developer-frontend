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

package modules.submissions.controllers.models

import cats.data.NonEmptyList
import domain.models.applications.ApplicationId
import modules.submissions.domain.models._

object AnswersViewModel {
  
  case class ViewQuestion(id: QuestionId, text: String, answer: String)
  case class ViewQuestionnaire(label: String, state: String, id: QuestionnaireId, questions: NonEmptyList[ViewQuestion])
  case class ViewModel(appId: ApplicationId, appName: String, questionnaires: List[ViewQuestionnaire])

  def convertAnswer(answer: ActualAnswer): Option[String] = answer match {
    case SingleChoiceAnswer(value) => Some(value)
    case TextAnswer(value) => Some(value)
    case MultipleChoiceAnswer(values) => Some(values.mkString)
    case NoAnswer => Some("n/a")
    case AcknowledgedAnswer => None
  }
  
  def convertQuestion(extSubmission: ExtendedSubmission)(item: QuestionItem): Option[ViewQuestion] = {
    val id = item.question.id
    
    extSubmission.submission.answersToQuestions.get(id).flatMap(convertAnswer).map(answer =>
      ViewQuestion(id, item.question.wording.value, answer)
    )
  }
  
  def convertQuestionnaire(extSubmission: ExtendedSubmission)(questionnaire: Questionnaire): Option[ViewQuestionnaire] = {
    val progress = extSubmission.questionnaireProgress.get(questionnaire.id).get
    val state = QuestionnaireState.describe(progress.state)
    
    val questions = questionnaire.questions
      .map(convertQuestion(extSubmission))
      .collect { case Some(x) => x }
    NonEmptyList.fromList(questions)
      .map(ViewQuestionnaire(questionnaire.label.value, state, questionnaire.id, _))
  }

  def convertSubmissionToViewModel(extSubmission: ExtendedSubmission)(appId: ApplicationId, appName: String): ViewModel = {
    val questionnaires = extSubmission.submission.groups.flatMap(g => g.links)
      .map(convertQuestionnaire(extSubmission))
      .collect { case Some(x) => x }

    ViewModel(appId, appName, questionnaires)
  }
}