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
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.apidefinitions.ApiIdentifier
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.{ApplicationId, UpliftData}

import scala.concurrent.Future

trait ApplicationUpliftSucceeds extends MockConnectors with HasApplicationData {
  when(apmConnector.upliftApplicationV2(*[ApplicationId], *[UpliftData])(*)).thenAnswer((appId: ApplicationId, _: UpliftData) => Future.successful(appId))
  when(apmConnector.fetchUpliftableApiIdentifiers(*)).thenReturn(Future.successful(Set.empty))
  when(apmConnector.fetchAllApis(*)(*)).thenReturn(Future.successful(Map.empty))
  when(apmConnector.fetchUpliftableSubscriptions(*[ApplicationId])(*)).thenReturn(Future.successful(Set(ApiIdentifier(apiContext, apiVersion))))
}
