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

package uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.connectors

import scala.concurrent.Future.successful
import org.mockito.{ArgumentMatchersSugar, MockitoSugar}
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.ThirdPartyDeveloperConnector
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.ThirdPartyApplicationConnector
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.BridgedConnector

trait ThirdPartyApplicationConnectorMockModule extends MockitoSugar with ArgumentMatchersSugar {

  trait AbstractThirdPartyApplicationConnectorMock {
    def aMock: ThirdPartyApplicationConnector
  }

  object PrincipalAppConnector extends AbstractThirdPartyApplicationConnectorMock {
    val aMock = mock[ThirdPartyApplicationConnector]
  }

  object SubordinateAppConnector extends AbstractThirdPartyApplicationConnectorMock {
    val aMock = mock[ThirdPartyApplicationConnector]
  }

  object BridgedAppConnector {
    lazy val connector = BridgedConnector[ThirdPartyApplicationConnector](SubordinateAppConnector.aMock, PrincipalAppConnector.aMock)
  }
}
