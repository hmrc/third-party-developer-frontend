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

package service

import cats.data.NonEmptyList
import connectors.{ApmConnector, ThirdPartyDeveloperConnector}
import domain.models.applications.ApplicationId
import domain.models.connectors.CombinedApi
import domain.models.developers.{DeveloperSession, UserId}
import domain.models.emailpreferences.APICategoryDisplayDetails
import domain.models.flows.{EmailPreferencesFlow, EmailPreferencesProducer, FlowType, NewApplicationEmailPreferencesFlow}
import repositories.FlowRepository
import repositories.ReactiveMongoFormatters.{formatEmailPreferencesFlow, formatNewApplicationEmailPreferencesFlow}
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}


@Singleton
class EmailPreferencesService @Inject()(val apmConnector: ApmConnector,
                                        val thirdPartyDeveloperConnector: ThirdPartyDeveloperConnector,
                                        val flowRepository: FlowRepository)(implicit val ec: ExecutionContext) {

  def fetchCategoriesVisibleToUser(developerSession: DeveloperSession, existingFlow: EmailPreferencesFlow)
                                  (implicit hc: HeaderCarrier): Future[List[APICategoryDisplayDetails]] =
    for {
      apis              <- getOrUpdateFlowWithVisibleApis(existingFlow, developerSession)
      visibleCategoryNames = apis.map(_.categories).reduce(_ ++ _).distinct.map(_.value).sorted
      categories        <- fetchAllAPICategoryDetails().map(_.filter(x => visibleCategoryNames.contains(x.category)))
    } yield categories.distinct.sortBy(_.category)


  private def getOrUpdateFlowWithVisibleApis(existingFlow: EmailPreferencesFlow, developerSession: DeveloperSession)
                                            (implicit hc: HeaderCarrier): Future[List[CombinedApi]] = {
    NonEmptyList.fromList(existingFlow.visibleApis.toList).fold({
      val visibleApis = apmConnector.fetchCombinedApisVisibleToUser(developerSession.developer.userId)
      updateVisibleApis(developerSession, apmConnector.fetchCombinedApisVisibleToUser(developerSession.developer.userId))
      visibleApis
    }) { x => Future.successful(x.toList) }
  }

  def fetchAllAPICategoryDetails()(implicit hc: HeaderCarrier): Future[List[APICategoryDisplayDetails]] = apmConnector.fetchAllAPICategories()

  def apiCategoryDetails(category: String)(implicit hc: HeaderCarrier): Future[Option[APICategoryDisplayDetails]] =
    fetchAllAPICategoryDetails().map(_.find(_.category == category))

  def fetchAPIDetails(apiServiceNames: Set[String])(implicit hc: HeaderCarrier): Future[List[CombinedApi]] =
    Future.sequence(
      apiServiceNames
        .map(apmConnector.fetchCombinedApi(_))
        .toList)
  
  def fetchEmailPreferencesFlow(developerSession: DeveloperSession) =
    flowRepository.fetchBySessionIdAndFlowType[EmailPreferencesFlow](developerSession.session.sessionId, FlowType.EMAIL_PREFERENCES) map {
      case Some(flow) => flow
      case None       => val newFlowObject = EmailPreferencesFlow.fromDeveloperSession(developerSession)
                          flowRepository.saveFlow[EmailPreferencesFlow](newFlowObject)
                          newFlowObject
    }

  def fetchNewApplicationEmailPreferencesFlow(developerSession: DeveloperSession, applicationId: ApplicationId): Future[NewApplicationEmailPreferencesFlow] =
    flowRepository.fetchBySessionIdAndFlowType[NewApplicationEmailPreferencesFlow](developerSession.session.sessionId, FlowType.NEW_APPLICATION_EMAIL_PREFERENCES) map {
      case Some(flow) => flow
      case None       => val newFlowObject = NewApplicationEmailPreferencesFlow(developerSession.session.sessionId, developerSession.developer.emailPreferences, applicationId, Set.empty, Set.empty, developerSession.developer.emailPreferences.topics.map(_.value))
                          flowRepository.saveFlow[NewApplicationEmailPreferencesFlow](newFlowObject)
                          newFlowObject
    }

  def deleteFlow(sessionId: String, flowType: FlowType): Future[Boolean] = flowRepository.deleteBySessionIdAndFlowType(sessionId, flowType)

  def updateCategories(developerSession: DeveloperSession, categoriesToAdd: List[String]): Future[EmailPreferencesFlow] = {
    for {
      existingFlow <- fetchEmailPreferencesFlow(developerSession)
      savedFlow    <- flowRepository.saveFlow[EmailPreferencesFlow](existingFlow.copy(selectedCategories = categoriesToAdd.toSet,
      selectedAPIs  = existingFlow.selectedAPIs.filter(api => categoriesToAdd.contains(api._1))))
    } yield savedFlow
  }

  def updateVisibleApis(developerSession: DeveloperSession, visibleApisF: Future[List[CombinedApi]]): Future[EmailPreferencesFlow] = {
    for {
      visibleApis  <- visibleApisF
      existingFlow <- fetchEmailPreferencesFlow(developerSession)
      savedFlow    <- flowRepository.saveFlow[EmailPreferencesFlow](existingFlow.copy(visibleApis = visibleApis))
    } yield savedFlow
  }

  def updateSelectedApis(developerSession: DeveloperSession, currentCategory: String, selectedApis: List[String]): Future[EmailPreferencesFlow] = {
    for {
      existingFlow <- fetchEmailPreferencesFlow(developerSession)
      updatedApis   = existingFlow.selectedAPIs ++ Map(currentCategory -> selectedApis.toSet)
      savedFlow    <- flowRepository.saveFlow[EmailPreferencesFlow](existingFlow.copy(selectedAPIs = updatedApis))
    } yield savedFlow
  }

  def updateMissingSubscriptions(developerSession: DeveloperSession,
                                 applicationId: ApplicationId,
                                 missingSubscriptions: Set[CombinedApi]): Future[NewApplicationEmailPreferencesFlow] = {
    for {
      existingFlow  <- fetchNewApplicationEmailPreferencesFlow(developerSession, applicationId)
      savedFlow     <- flowRepository.saveFlow[NewApplicationEmailPreferencesFlow](existingFlow.copy(missingSubscriptions = missingSubscriptions))
    } yield savedFlow
  }

  def updateNewApplicationSelectedApis(developerSession: DeveloperSession,
                                       applicationId: ApplicationId,
                                       selectedApis: Set[String])(implicit hc: HeaderCarrier) = {
    for {
      apis <- fetchAPIDetails(selectedApis)
      existingFlow  <- fetchNewApplicationEmailPreferencesFlow(developerSession, applicationId)
      savedFlow     <- flowRepository.saveFlow[NewApplicationEmailPreferencesFlow](existingFlow.copy(selectedApis = apis.toSet))
    } yield savedFlow
  }

  def removeEmailPreferences(userId: UserId)(implicit hc: HeaderCarrier): Future[Boolean] =
    thirdPartyDeveloperConnector.removeEmailPreferences(userId)

  def updateEmailPreferences(userId: UserId, flow: EmailPreferencesProducer)(implicit hc: HeaderCarrier): Future[Boolean] =
    thirdPartyDeveloperConnector.updateEmailPreferences(userId, flow.toEmailPreferences)
}
