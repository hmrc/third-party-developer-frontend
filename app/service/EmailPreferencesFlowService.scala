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

import domain.models.flows.EmailPreferencesFlow
import javax.inject.{Inject, Singleton}
import repositories.FlowRepository
import repositories.ReactiveMongoFormatters.formatEmailPreferencesFlow
import domain.models.developers.DeveloperSession


import scala.concurrent.{ExecutionContext, Future}

@Singleton
class EmailPreferencesFlowService @Inject()(val flowRepository: FlowRepository)(implicit val ec: ExecutionContext) {

    def fetchFlowBySessionId(developerSession: DeveloperSession): Future[EmailPreferencesFlow]= {
        flowRepository.fetchBySessionId(developerSession.session.sessionId) map {
            case Some(flow) => flow
            case _ => EmailPreferencesFlow.fromDeveloperSession(developerSession)
        }
    }
}
