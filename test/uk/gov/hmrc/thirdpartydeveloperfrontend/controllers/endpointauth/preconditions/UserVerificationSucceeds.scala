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

import play.api.http.Status.OK
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.endpointauth.MockConnectors
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.ApplicationVerificationSuccessful

import scala.concurrent.Future

trait UserVerificationSucceeds extends MockConnectors {
  when(tpdConnector.verify(*)(*)).thenReturn(Future.successful(OK))
  when(tpaProductionConnector.verify(*)(*)).thenReturn(Future.successful(ApplicationVerificationSuccessful))
  when(tpdConnector.resendVerificationEmail(*)(*)).thenReturn(Future.successful(OK))
}
