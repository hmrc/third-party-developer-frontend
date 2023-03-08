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

package uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.service

import org.mockito.{ArgumentMatchersSugar, MockitoSugar}
import uk.gov.hmrc.apiplatform.modules.applications.services.CollaboratorService
import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.ApplicationUpdateSuccessful
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.Application
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.DeveloperSession

import scala.concurrent.Future.successful

trait CollaboratorServiceMock extends MockitoSugar with ArgumentMatchersSugar{


  val collaboratorServiceMock = mock[CollaboratorService]

  def givenRemoveTeamMemberSucceeds(loggedInDeveloper:
                                    DeveloperSession) =
    when(collaboratorServiceMock.removeTeamMember(*, *[LaxEmailAddress], eqTo(loggedInDeveloper.email))(*))
      .thenReturn(successful(ApplicationUpdateSuccessful))

  def verifyNeverCallRemoveTeamMember() =
    verify(collaboratorServiceMock, never).removeTeamMember(any[Application], *[LaxEmailAddress], *[LaxEmailAddress])(*)
}
