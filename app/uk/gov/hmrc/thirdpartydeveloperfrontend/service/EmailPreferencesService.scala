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

package uk.gov.hmrc.thirdpartydeveloperfrontend.service

import javax.inject.{Inject, Singleton}
import scala.concurrent.Future.successful
import scala.concurrent.{ExecutionContext, Future}

import cats.data.NonEmptyList

import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apiplatform.modules.common.domain.models.{ApplicationId, UserId}
import uk.gov.hmrc.apiplatform.modules.common.services.ApplicationLogger
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.{ApmConnector, ThirdPartyDeveloperConnector}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors.ApiType.REST_API
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors.{CombinedApi}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.DeveloperSession
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.emailpreferences.APICategoryDisplayDetails
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.flows.{EmailPreferencesFlowV2, EmailPreferencesProducer, FlowType, NewApplicationEmailPreferencesFlowV2}
import uk.gov.hmrc.thirdpartydeveloperfrontend.repositories.FlowRepository

@Singleton
class EmailPreferencesService @Inject() (
    val apmConnector: ApmConnector,
    val thirdPartyDeveloperConnector: ThirdPartyDeveloperConnector,
    val flowRepository: FlowRepository
  )(implicit val ec: ExecutionContext
  ) extends ApplicationLogger {

  def fetchCategoriesVisibleToUser(developerSession: DeveloperSession, existingFlow: EmailPreferencesFlowV2)(implicit hc: HeaderCarrier): Future[List[APICategoryDisplayDetails]] =
    for {
      apis                <- getOrUpdateFlowWithVisibleApis(existingFlow, developerSession)
      visibleCategoryNames = apis.map(_.categories).reduce(_ ++ _).distinct.map(_.toString())
      categories          <- fetchAllAPICategoryDetails().map(_.filter(x => visibleCategoryNames.contains(x.category)))
    } yield categories.distinct.sortBy(_.category)

  private def getOrUpdateFlowWithVisibleApis(existingFlow: EmailPreferencesFlowV2, developerSession: DeveloperSession)(implicit hc: HeaderCarrier): Future[List[CombinedApi]] = {
    NonEmptyList.fromList(existingFlow.visibleApis.toList).fold({
      val visibleApis = apmConnector.fetchCombinedApisVisibleToUser(developerSession.developer.userId)
        .flatMap {
          case Right(x) => successful(x)
          case Left(_)  => apmConnector.fetchApiDefinitionsVisibleToUser(developerSession.developer.userId).map(_.map(y =>
              CombinedApi(y.serviceName, y.name, y.categories, REST_API)
            ))
        }
      updateVisibleApis(developerSession, visibleApis)
      visibleApis
    }) { x => Future.successful(x.toList) }
  }

  def fetchAllAPICategoryDetails()(implicit hc: HeaderCarrier): Future[List[APICategoryDisplayDetails]] = {
    apmConnector.fetchAllCombinedAPICategories().flatMap {
      case Right(x)           => successful(x)
      case Left(e: Throwable) =>
        logger.error(s"fetchAllAPICategoryDetails failed: ${e.getMessage}")
        successful(List.empty)
    }
  }

  def apiCategoryDetails(category: String)(implicit hc: HeaderCarrier): Future[Option[APICategoryDisplayDetails]] =
    fetchAllAPICategoryDetails().map(_.find(_.category == category))

  private def handleGettingApiDetails(serviceName: String)(implicit hc: HeaderCarrier): Future[CombinedApi] = {
    apmConnector.fetchCombinedApi(serviceName).flatMap {
      case Right(x) => successful(x)
      case Left(_)  => apmConnector.fetchAPIDefinition(serviceName).map(y => CombinedApi(y.serviceName, y.name, y.categories, REST_API))
    }
  }

  def fetchAPIDetails(apiServiceNames: Set[String])(implicit hc: HeaderCarrier): Future[List[CombinedApi]] =
    Future.sequence(
      apiServiceNames
        .map(handleGettingApiDetails(_))
        .toList
    )

  def fetchEmailPreferencesFlow(developerSession: DeveloperSession) =
    flowRepository.fetchBySessionIdAndFlowType[EmailPreferencesFlowV2](developerSession.session.sessionId) map {
      case Some(flow) => flow
      case None       =>
        val newFlowObject = EmailPreferencesFlowV2.fromDeveloperSession(developerSession)
        flowRepository.saveFlow[EmailPreferencesFlowV2](newFlowObject)
        newFlowObject
    }

  def fetchNewApplicationEmailPreferencesFlow(developerSession: DeveloperSession, applicationId: ApplicationId): Future[NewApplicationEmailPreferencesFlowV2] =
    flowRepository.fetchBySessionIdAndFlowType[NewApplicationEmailPreferencesFlowV2](developerSession.session.sessionId) map {
      case Some(flow) => flow
      case None       =>
        val newFlowObject = NewApplicationEmailPreferencesFlowV2(
          developerSession.session.sessionId,
          developerSession.developer.emailPreferences,
          applicationId,
          Set.empty,
          Set.empty,
          developerSession.developer.emailPreferences.topics.map(_.value)
        )
        flowRepository.saveFlow[NewApplicationEmailPreferencesFlowV2](newFlowObject)
        newFlowObject
    }

  def deleteFlow(sessionId: String, flowType: FlowType): Future[Boolean] = flowRepository.deleteBySessionIdAndFlowType(sessionId, flowType)

  def updateCategories(developerSession: DeveloperSession, categoriesToAdd: List[String]): Future[EmailPreferencesFlowV2] = {
    for {
      existingFlow <- fetchEmailPreferencesFlow(developerSession)
      savedFlow    <- flowRepository.saveFlow[EmailPreferencesFlowV2](existingFlow.copy(
                        selectedCategories = categoriesToAdd.toSet,
                        selectedAPIs = existingFlow.selectedAPIs.filter(api => categoriesToAdd.contains(api._1))
                      ))
    } yield savedFlow
  }

  def updateVisibleApis(developerSession: DeveloperSession, visibleApisF: Future[List[CombinedApi]]): Future[EmailPreferencesFlowV2] = {
    for {
      visibleApis  <- visibleApisF
      existingFlow <- fetchEmailPreferencesFlow(developerSession)
      savedFlow    <- flowRepository.saveFlow[EmailPreferencesFlowV2](existingFlow.copy(visibleApis = visibleApis))
    } yield savedFlow
  }

  def updateSelectedApis(developerSession: DeveloperSession, currentCategory: String, selectedApis: List[String]): Future[EmailPreferencesFlowV2] = {
    for {
      existingFlow <- fetchEmailPreferencesFlow(developerSession)
      updatedApis   = existingFlow.selectedAPIs ++ Map(currentCategory -> selectedApis.toSet)
      savedFlow    <- flowRepository.saveFlow[EmailPreferencesFlowV2](existingFlow.copy(selectedAPIs = updatedApis))
    } yield savedFlow
  }

  def updateMissingSubscriptions(
      developerSession: DeveloperSession,
      applicationId: ApplicationId,
      missingSubscriptions: Set[CombinedApi]
    ): Future[NewApplicationEmailPreferencesFlowV2] = {
    for {
      existingFlow <- fetchNewApplicationEmailPreferencesFlow(developerSession, applicationId)
      savedFlow    <- flowRepository.saveFlow[NewApplicationEmailPreferencesFlowV2](existingFlow.copy(missingSubscriptions = missingSubscriptions))
    } yield savedFlow
  }

  def updateNewApplicationSelectedApis(developerSession: DeveloperSession, applicationId: ApplicationId, selectedApis: Set[String])(implicit hc: HeaderCarrier) = {
    for {
      apis         <- fetchAPIDetails(selectedApis)
      existingFlow <- fetchNewApplicationEmailPreferencesFlow(developerSession, applicationId)
      savedFlow    <- flowRepository.saveFlow[NewApplicationEmailPreferencesFlowV2](existingFlow.copy(selectedApis = apis.toSet))
    } yield savedFlow
  }

  def removeEmailPreferences(userId: UserId)(implicit hc: HeaderCarrier): Future[Boolean] =
    thirdPartyDeveloperConnector.removeEmailPreferences(userId)

  def updateEmailPreferences(userId: UserId, flow: EmailPreferencesProducer)(implicit hc: HeaderCarrier): Future[Boolean] =
    thirdPartyDeveloperConnector.updateEmailPreferences(userId, flow.toEmailPreferences)
}
