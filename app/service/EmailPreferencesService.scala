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
import connectors.{ApmConnector, ThirdPartyDeveloperConnector}
import domain.models.connectors.{ApiDefinition, ExtendedApiDefinition}
import domain.models.developers.DeveloperSession
import domain.models.emailpreferences.APICategoryDetails
import domain.models.flows.{EmailPreferencesFlow, FlowType}
import javax.inject.{Inject, Singleton}
import repositories.FlowRepository
import repositories.ReactiveMongoFormatters.formatEmailPreferencesFlow
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}


@Singleton
class EmailPreferencesService @Inject()(val apmConnector: ApmConnector,
                                        val thirdPartyDeveloperConnector: ThirdPartyDeveloperConnector,
                                        val flowRepository: FlowRepository)(implicit val ec: ExecutionContext) {

  def fetchCategoriesVisibleToUser(developerSession: DeveloperSession)(implicit hc: HeaderCarrier): Future[Seq[APICategoryDetails]] =
    for {
      existingFlow <- fetchFlow(developerSession)
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

  def updateEmailPreferences(emailAddress: String, flow: EmailPreferencesFlow)(implicit hc: HeaderCarrier): Future[Boolean] = {
    thirdPartyDeveloperConnector.updateEmailPreferences(emailAddress, flow.toEmailPreferences)
  }

  def fetchFlow(developerSession: DeveloperSession): Future[EmailPreferencesFlow] =
    flowRepository.fetchBySessionIdAndFlowType[EmailPreferencesFlow](developerSession.session.sessionId, FlowType.EMAIL_PREFERENCES) map {
      case Some(flow) => flow
      case None => val newFlowObject = EmailPreferencesFlow.fromDeveloperSession(developerSession)
        flowRepository.saveFlow[EmailPreferencesFlow](newFlowObject)
        newFlowObject
    }

  def deleteFlow(sessionId: String): Future[Boolean] = flowRepository.deleteBySessionIdAndFlowType(sessionId, FlowType.EMAIL_PREFERENCES)

  def updateCategories(developerSession: DeveloperSession, categoriesToAdd: List[String]): Future[EmailPreferencesFlow] = {
    for {
      existingFlow <- fetchFlow(developerSession)
      savedFlow <- flowRepository.saveFlow[EmailPreferencesFlow](existingFlow.copy(selectedCategories = categoriesToAdd.toSet,
        selectedAPIs = existingFlow.selectedAPIs.filter(api => categoriesToAdd.contains(api._1))))
    } yield savedFlow
  }

  def updateVisibleApis(developerSession: DeveloperSession, visibleApisF: Future[Seq[ApiDefinition]]): Future[EmailPreferencesFlow] = {
    for {
      visibleApis <- visibleApisF
      existingFlow <- fetchFlow(developerSession)
      savedFlow <- flowRepository.saveFlow[EmailPreferencesFlow](existingFlow.copy(visibleApis = visibleApis))
    } yield savedFlow
  }

  def updateSelectedApis(developerSession: DeveloperSession, currentCategory: String, selectedApis: List[String]) = {

    for {
      existingFlow <- fetchFlow(developerSession)
      updatedApis = existingFlow.selectedAPIs ++ Map(currentCategory -> selectedApis.toSet)
      savedFlow <- flowRepository.saveFlow[EmailPreferencesFlow](existingFlow.copy(selectedAPIs = updatedApis))
    } yield savedFlow
  }


}
