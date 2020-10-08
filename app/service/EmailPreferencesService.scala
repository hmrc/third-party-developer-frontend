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

package service

import cats.data.NonEmptyList
import javax.inject.{Inject, Singleton}

import scala.concurrent.{ExecutionContext, Future}
import connectors.ThirdPartyDeveloperConnector
import domain.models.developers.DeveloperSession
import domain.models.flows.{EmailPreferencesFlow, FlowType}
import repositories.ReactiveMongoFormatters.formatEmailPreferencesFlow
import repositories.FlowRepository
import uk.gov.hmrc.http.HeaderCarrier
import connectors.ApmConnector
import domain.models.connectors.{ApiDefinition, ExtendedApiDefinition}
import domain.models.emailpreferences.APICategoryDetails
import domain.models.emailpreferences.EmailPreferences


@Singleton
class EmailPreferencesService @Inject()(val apmConnector: ApmConnector,
                                        val thirdPartyDeveloperConnector: ThirdPartyDeveloperConnector,
                                        val flowRepository: FlowRepository)(implicit val ec: ExecutionContext) {

  def fetchCategoriesVisibleToUser(developerSession: DeveloperSession)(implicit hc: HeaderCarrier): Future[Seq[APICategoryDetails]] =
    for {
      existingFlow <- fetchFlowBySessionId(developerSession)
      apis <- getOrUpdateFlowWithVisibleApis(existingFlow, developerSession)
      visibleCategories = apis.map(_.categories).reduce(_ ++ _).distinct.sorted
      categories <- fetchAllAPICategoryDetails().map(_.filter(x => visibleCategories.contains(x.category)))
    } yield categories


  private def getOrUpdateFlowWithVisibleApis(existingFlow: EmailPreferencesFlow, developerSession: DeveloperSession)
                                            (implicit hc: HeaderCarrier): Future[Seq[ApiDefinition]] = {
    NonEmptyList.fromList(existingFlow.visibleApis.toList).fold({
      val visibleApis = apmConnector.fetchApiDefinitionsVisibleToUser(developerSession.developer.email)
      updateVisibleApis(developerSession, apmConnector.fetchApiDefinitionsVisibleToUser(developerSession.developer.email))
      visibleApis
    }) { x => Future.successful(x.toList) }
  }

  def fetchAllAPICategoryDetails()(implicit hc: HeaderCarrier): Future[Seq[APICategoryDetails]] = apmConnector.fetchAllAPICategories()

  def apiCategoryDetails(category: String)(implicit hc: HeaderCarrier): Future[Option[APICategoryDetails]] =
    fetchAllAPICategoryDetails().map(_.find(_.category == category))

  def fetchAPIDetails(apiServiceNames: Set[String])(implicit hc: HeaderCarrier): Future[Seq[ExtendedApiDefinition]] =
    Future.sequence(
      apiServiceNames
        .map(apmConnector.fetchAPIDefinition(_))
        .toSeq)

  def removeEmailPreferences(emailAddress: String)(implicit hc: HeaderCarrier): Future[Boolean] = {
    thirdPartyDeveloperConnector.removeEmailPreferences(emailAddress)
  }

  def updateEmailPreferences(emailAddress: String, flow: EmailPreferencesFlow)(implicit hc: HeaderCarrier): Future[Boolean] ={
    thirdPartyDeveloperConnector.updateEmailPreferences(emailAddress, flow.toEmailPreferences)
  }

  def fetchFlowBySessionId(developerSession: DeveloperSession): Future[EmailPreferencesFlow] = {
    flowRepository.fetchBySessionIdAndFlowType[EmailPreferencesFlow](developerSession.session.sessionId, FlowType.EMAIL_PREFERENCES) map {
      case Some(flow) => flow
      case None => val newFlowObject = EmailPreferencesFlow.fromDeveloperSession(developerSession)
        flowRepository.saveFlow[EmailPreferencesFlow](newFlowObject)
        newFlowObject
    }
  }

  def deleteFlowBySessionId(developerSession: DeveloperSession):  Future[Boolean] = {
    flowRepository.deleteBySessionIdAndFlowType(developerSession.session.sessionId, FlowType.EMAIL_PREFERENCES)
  }

  def updateCategories(developerSession: DeveloperSession, categoriesToAdd: List[String]): Future[EmailPreferencesFlow] = {
    for {
      existingFlow <- fetchFlowBySessionId(developerSession)
      savedFlow <- flowRepository.saveFlow[EmailPreferencesFlow](existingFlow.copy(selectedCategories = categoriesToAdd.toSet,
        selectedAPIs = existingFlow.selectedAPIs.filter(api => categoriesToAdd.contains(api._1))))
    } yield savedFlow
  }

  def updateVisibleApis(developerSession: DeveloperSession, visibleApisF: Future[Seq[ApiDefinition]]): Future[EmailPreferencesFlow] = {
    for {
      visibleApis <- visibleApisF
      existingFlow <- fetchFlowBySessionId(developerSession)
      savedFlow <- flowRepository.saveFlow[EmailPreferencesFlow](existingFlow.copy(visibleApis = visibleApis))
    } yield savedFlow
  }

  def updateSelectedApis(developerSession: DeveloperSession, currentCategory: String, selectedApis: List[String]) = {

    //get  apis for category... if exists...? do we care? just overrite

    for {
      existingFlow <- fetchFlowBySessionId(developerSession)
      updatedApis = existingFlow.selectedAPIs ++ Map(currentCategory -> selectedApis.toSet)
      savedFlow <- flowRepository.saveFlow[EmailPreferencesFlow](existingFlow.copy(selectedAPIs = updatedApis))
    } yield savedFlow
  }


}
