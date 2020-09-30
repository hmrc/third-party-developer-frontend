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
import domain.models.flows.EmailPreferencesFlow
import repositories.ReactiveMongoFormatters.formatEmailPreferencesFlow
import repositories.FlowRepository
import uk.gov.hmrc.http.HeaderCarrier

@Singleton
class EmailPreferencesService @Inject()(val thirdPartyDeveloperConnector: ThirdPartyDeveloperConnector, flowRepository: FlowRepository)(implicit val ec: ExecutionContext) {

    def removeEmailPreferences(emailAddress: String)(implicit hc: HeaderCarrier): Future[Boolean] = {
        thirdPartyDeveloperConnector.removeEmailPreferences(emailAddress)
    }

    def fetchFlowBySessionId(developerSession: DeveloperSession): Future[EmailPreferencesFlow]= {
        flowRepository.fetchBySessionId[EmailPreferencesFlow](developerSession.session.sessionId) map {
            case Some(flow) => {println("*** - EXISTINGFLOW - ****")
                flow
            }
            case None => { 
                println("*** - NEW FLOW - ****")
                val newFlowObject = EmailPreferencesFlow.fromDeveloperSession(developerSession)
             flowRepository.saveFlow[EmailPreferencesFlow](newFlowObject)
             newFlowObject
            }

        }
    }

    def updateCategories(developerSession: DeveloperSession, categoriesToAdd: List[String]): Future[EmailPreferencesFlow] = {
        for{ 
            existingFlow <- fetchFlowBySessionId(developerSession)
            savedFlow <-  flowRepository.saveFlow[EmailPreferencesFlow](existingFlow.copy(selectedCategories = categoriesToAdd.toSet,
             selectedAPIs = existingFlow.selectedAPIs.filter(api => categoriesToAdd.contains(api._1))))
        } yield savedFlow   
    }

}
