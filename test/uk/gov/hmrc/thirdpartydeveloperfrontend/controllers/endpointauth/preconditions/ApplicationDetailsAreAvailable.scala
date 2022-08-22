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

package uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.endpointauth.preconditions

import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.endpointauth.MockConnectors
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._

import java.time.LocalDateTime
import scala.concurrent.Future

trait ApplicationDetailsAreAvailable extends MockConnectors with UserIsAuthenticated with HasApplicationData {
  when(apmConnector.fetchApplicationById(*[ApplicationId])(*)).thenReturn(Future.successful(Some(appWithSubsData)))
  when(apmConnector.getAllFieldDefinitions(*[Environment])(*)).thenReturn(Future.successful(Map(apiContext -> Map(apiVersion -> subscriptionFieldDefinitions))))
  when(apmConnector.fetchAllOpenAccessApis(*[Environment])(*)).thenReturn(Future.successful(Map.empty))
  when(apmConnector.fetchAllPossibleSubscriptions(*[ApplicationId])(*)).thenReturn(Future.successful(allPossibleSubscriptions))
  when(tpaProductionConnector.fetchCredentials(*[ApplicationId])(*)).thenReturn(Future.successful(ApplicationToken(List(ClientSecret("s1id", "s1name", LocalDateTime.now(), None)), "secret")))
  when(productionPushPullNotificationsConnector.fetchPushSecrets(*[ClientId])(*)).thenReturn(Future.successful(List("secret1")))
}
