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

import uk.gov.hmrc.apiplatform.modules.apis.domain.models.{ApiCategory, ServiceName}
import uk.gov.hmrc.apiplatform.modules.common.domain.models.{ApplicationId, UserId}
import uk.gov.hmrc.apiplatform.modules.common.services.ApplicationLogger
import uk.gov.hmrc.apiplatform.modules.tpd.core.domain.models.SessionId
import uk.gov.hmrc.apiplatform.modules.tpd.session.domain.models.UserSession
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.{ApmConnector, ThirdPartyDeveloperConnector}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors.ApiType.REST_API
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors.CombinedApi
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

  def fetchCategoriesVisibleToUser(userSession: UserSession, existingFlow: EmailPreferencesFlowV2)(implicit hc: HeaderCarrier): Future[List[APICategoryDisplayDetails]] =
    for {
      apis                <- getOrUpdateFlowWithVisibleApis(existingFlow, userSession)
      visibleCategoryNames = apis.map(_.categories).reduce(_ ++ _).distinct.map(_.toString())
      categories          <- fetchAllAPICategoryDetails().map(_.filter(x => visibleCategoryNames.contains(x.category)))
    } yield categories.distinct.sortBy(_.category)

  private def getOrUpdateFlowWithVisibleApis(existingFlow: EmailPreferencesFlowV2, userSession: UserSession)(implicit hc: HeaderCarrier): Future[List[CombinedApi]] = {
    NonEmptyList.fromList(existingFlow.visibleApis.toList).fold({
      val visibleApis = apmConnector.fetchCombinedApisVisibleToUser(userSession.developer.userId)
        .flatMap {
          case Right(x) => successful(x)
          case Left(_)  => apmConnector.fetchApiDefinitionsVisibleToUser(Some(userSession.developer.userId)).map(_.map(y =>
              CombinedApi(y.serviceName, y.name, y.categories, REST_API)
            ))
        }
      updateVisibleApis(userSession, visibleApis)
      visibleApis
    }) { x => Future.successful(x.toList) }
  }

  def fetchAllAPICategoryDetails(): Future[List[APICategoryDisplayDetails]] = {
    val categories = ApiCategory.values
    successful(categories.map(c => APICategoryDisplayDetails(c.toString(), c.displayText)).toList)
  }

  def apiCategoryDetails(category: String): Future[Option[APICategoryDisplayDetails]] =
    fetchAllAPICategoryDetails().map(_.find(_.category == category))

  private def handleGettingApiDetails(serviceName: ServiceName)(implicit hc: HeaderCarrier): Future[Option[CombinedApi]] = {
    apmConnector.fetchCombinedApi(serviceName).flatMap {
      case Right(x) => successful(Some(x))
      case Left(_)  => apmConnector.fetchExtendedApiDefinition(serviceName).flatMap {
          case Right(y) => successful(Some(CombinedApi(y.serviceName, y.name, y.categories, REST_API)))
          case Left(_)  => successful(None)
        }
    }
  }

  def fetchAPIDetails(apiServiceNames: Set[ServiceName])(implicit hc: HeaderCarrier): Future[List[CombinedApi]] =
    Future.sequence(
      apiServiceNames
        .map(
          handleGettingApiDetails(_)
        )
        .toList
    ).map(_.flatten)

  def fetchEmailPreferencesFlow(userSession: UserSession) =
    flowRepository.fetchBySessionIdAndFlowType[EmailPreferencesFlowV2](userSession.sessionId) map {
      case Some(flow) => flow
      case None       =>
        val newFlowObject = EmailPreferencesFlowV2.fromDeveloperSession(userSession)
        flowRepository.saveFlow[EmailPreferencesFlowV2](newFlowObject)
        newFlowObject
    }

  def fetchNewApplicationEmailPreferencesFlow(userSession: UserSession, applicationId: ApplicationId): Future[NewApplicationEmailPreferencesFlowV2] =
    flowRepository.fetchBySessionIdAndFlowType[NewApplicationEmailPreferencesFlowV2](userSession.sessionId) map {
      case Some(flow) => flow
      case None       =>
        val newFlowObject = NewApplicationEmailPreferencesFlowV2(
          userSession.sessionId,
          userSession.developer.emailPreferences,
          applicationId,
          Set.empty,
          Set.empty,
          userSession.developer.emailPreferences.topics.map(_.toString)
        )
        flowRepository.saveFlow[NewApplicationEmailPreferencesFlowV2](newFlowObject)
        newFlowObject
    }

  def deleteFlow(sessionId: SessionId, flowType: FlowType): Future[Boolean] = flowRepository.deleteBySessionIdAndFlowType(sessionId, flowType)

  def updateCategories(userSession: UserSession, categoriesToAdd: List[String]): Future[EmailPreferencesFlowV2] = {
    for {
      existingFlow <- fetchEmailPreferencesFlow(userSession)
      savedFlow    <- flowRepository.saveFlow[EmailPreferencesFlowV2](existingFlow.copy(
                        selectedCategories = categoriesToAdd.toSet,
                        selectedAPIs = existingFlow.selectedAPIs.filter(api => categoriesToAdd.contains(api._1))
                      ))
    } yield savedFlow
  }

  def updateVisibleApis(userSession: UserSession, visibleApisF: Future[List[CombinedApi]]): Future[EmailPreferencesFlowV2] = {
    for {
      visibleApis  <- visibleApisF
      existingFlow <- fetchEmailPreferencesFlow(userSession)
      savedFlow    <- flowRepository.saveFlow[EmailPreferencesFlowV2](existingFlow.copy(visibleApis = visibleApis))
    } yield savedFlow
  }

  def updateSelectedApis(userSession: UserSession, currentCategory: String, selectedApis: List[String]): Future[EmailPreferencesFlowV2] = {
    for {
      existingFlow <- fetchEmailPreferencesFlow(userSession)
      updatedApis   = existingFlow.selectedAPIs ++ Map(currentCategory -> selectedApis.toSet)
      savedFlow    <- flowRepository.saveFlow[EmailPreferencesFlowV2](existingFlow.copy(selectedAPIs = updatedApis))
    } yield savedFlow
  }

  def updateMissingSubscriptions(
      userSession: UserSession,
      applicationId: ApplicationId,
      missingSubscriptions: Set[CombinedApi]
    ): Future[NewApplicationEmailPreferencesFlowV2] = {
    for {
      existingFlow <- fetchNewApplicationEmailPreferencesFlow(userSession, applicationId)
      savedFlow    <- flowRepository.saveFlow[NewApplicationEmailPreferencesFlowV2](existingFlow.copy(missingSubscriptions = missingSubscriptions))
    } yield savedFlow
  }

  def updateNewApplicationSelectedApis(userSession: UserSession, applicationId: ApplicationId, selectedApis: Set[ServiceName])(implicit hc: HeaderCarrier) = {
    for {
      apis         <- fetchAPIDetails(selectedApis)
      existingFlow <- fetchNewApplicationEmailPreferencesFlow(userSession, applicationId)
      savedFlow    <- flowRepository.saveFlow[NewApplicationEmailPreferencesFlowV2](existingFlow.copy(selectedApis = apis.toSet))
    } yield savedFlow
  }

  def removeEmailPreferences(userId: UserId)(implicit hc: HeaderCarrier): Future[Boolean] =
    thirdPartyDeveloperConnector.removeEmailPreferences(userId)

  def updateEmailPreferences(userId: UserId, flow: EmailPreferencesProducer)(implicit hc: HeaderCarrier): Future[Boolean] =
    thirdPartyDeveloperConnector.updateEmailPreferences(userId, flow.toEmailPreferences)
}
