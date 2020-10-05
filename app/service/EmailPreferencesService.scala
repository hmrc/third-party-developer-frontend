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


@Singleton
class EmailPreferencesService @Inject()(val apmConnector: ApmConnector,
                                        val thirdPartyDeveloperConnector: ThirdPartyDeveloperConnector,
                                        val flowRepository: FlowRepository)(implicit val ec: ExecutionContext) {

  def removeEmailPreferences(emailAddress: String)(implicit hc: HeaderCarrier): Future[Boolean] = {
    thirdPartyDeveloperConnector.removeEmailPreferences(emailAddress)
  }

  def fetchFlowBySessionId(developerSession: DeveloperSession): Future[EmailPreferencesFlow] = {
    flowRepository.fetchBySessionIdAndFlowType[EmailPreferencesFlow](developerSession.session.sessionId, FlowType.EMAIL_PREFERENCES) map {
      case Some(flow) => flow
      case None => val newFlowObject = EmailPreferencesFlow.fromDeveloperSession(developerSession)
        flowRepository.saveFlow[EmailPreferencesFlow](newFlowObject)
        newFlowObject
    }
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
    updatedApis  = existingFlow.selectedAPIs ++ Map(currentCategory -> selectedApis.toSet)
    savedFlow <- flowRepository.saveFlow[EmailPreferencesFlow](existingFlow.copy(selectedAPIs = updatedApis))
  } yield savedFlow
}


def fetchApiDefinitionsVisibleToUser (developerSession: DeveloperSession) (implicit hc: HeaderCarrier): Future[Seq[ApiDefinition]] = {
  for {
  existingFlow <- fetchFlowBySessionId (developerSession)
  apis <- if (existingFlow.visibleApis.nonEmpty) {
  Future.successful (existingFlow.visibleApis)
} else {
  val visibleApis = apmConnector.fetchApiDefinitionsVisibleToUser (developerSession.developer.email)
  updateVisibleApis (developerSession, visibleApis)
  visibleApis
}
} yield apis
}


  //     def apisByCategory(userEmail: String): Map[String, Seq[ExtendedAPIDefinition]] = {
  //         for {
  //             apis <- apmConnector.fetchApiDefinitionsVisibleToUser(userEmail)

  //         }yield apis.groupBy(api => api.)

  //     }


  //     def servicesByCategory(apiDefinitions: Seq[APIDefinition]): Map[APICategory, Set[String]] = {

  //     def serviceCategories(apiDefinition: APIDefinition): Map[APICategory, Set[String]] =
  //       apiDefinition.categories
  //         .map(category => APICategory.withName(category) -> Set(apiDefinition.serviceName))
  //         .toMap

  //     apiDefinitions
  //       .map(serviceCategories)
  //       .fold(Map.empty)(_ combine _)
  //   }

}
