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

package uk.gov.hmrc.apiplatform.modules.submissions.controllers


import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ApplicationConfig
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ErrorHandler
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.ApmConnector
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.ApplicationId
import play.api.libs.crypto.CookieSigner
import play.api.mvc.{MessagesControllerComponents, Result}
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.{ApplicationActionService, ApplicationService, SessionService}
import uk.gov.hmrc.apiplatform.modules.submissions.views.html._

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.ApplicationController
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.checkpages.CanUseCheckActions
import cats.data.NonEmptyList
import uk.gov.hmrc.apiplatform.modules.submissions.domain.models._
import uk.gov.hmrc.apiplatform.modules.submissions.services.SubmissionService
import uk.gov.hmrc.apiplatform.modules.common.services.EitherTHelper
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.controllers.BadRequestWithErrorMessage
import uk.gov.hmrc.apiplatform.modules.submissions.controllers.models.AnswersViewModel._

object CredentialsRequestedController {
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
    
    extSubmission.submission.latestInstance.answersToQuestions.get(id).flatMap(convertAnswer).map(answer =>
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

@Singleton
class CredentialsRequestedController @Inject() (
    val errorHandler: ErrorHandler,
    val sessionService: SessionService,
    val applicationActionService: ApplicationActionService,
    val applicationService: ApplicationService,
    mcc: MessagesControllerComponents,
    val cookieSigner: CookieSigner,
    val apmConnector: ApmConnector,
    val submissionService: SubmissionService,
    credentialsRequestedView: CredentialsRequestedView)
    (implicit val ec: ExecutionContext, val appConfig: ApplicationConfig)
  extends ApplicationController(mcc)
     with CanUseCheckActions
     with EitherTHelper[String]
     with SubmissionActionBuilders {


  import SubmissionActionBuilders.{StateFilter,RoleFilter}
  import cats.implicits._
  import cats.instances.future.catsStdInstancesForFuture
  
  val redirectToGetProdCreds = (applicationId: ApplicationId) => Redirect(routes.ProdCredsChecklistController.productionCredentialsChecklistPage(applicationId))

   /*, Read/Write and State details */ 
  def credentialsRequestedPage(productionAppId: ApplicationId) = withApplicationAndCompletedSubmission(StateFilter.pendingApproval, RoleFilter.isTeamMember)(redirectToGetProdCreds(productionAppId))(productionAppId) { implicit request =>
    val failed = (err: String) => BadRequestWithErrorMessage(err)
    
    val success = (viewModel: ViewModel) => {
      val err = request.msgRequest.flash.get("error")
      Ok(credentialsRequestedView(viewModel, err))
    }

    val vm = for {
      submission          <- fromOptionF(submissionService.fetchLatestSubmission(productionAppId), "No submission and/or application found")
      viewModel           =  convertSubmissionToViewModel(submission)(request.application.id, request.application.name)
    } yield viewModel

    vm.fold[Result](failed, success)
  }
}
