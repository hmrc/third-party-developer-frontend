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

import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.ApplicationWithCollaborators
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.TermsOfUseVersion
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.TermsOfUseVersionService

trait TermsOfUseVersionServiceMock extends MockitoSugar with ArgumentMatchersSugar {
  val termsOfUseVersionServiceMock = mock[TermsOfUseVersionService]

  def returnLatestTermsOfUseVersion = when(termsOfUseVersionServiceMock.getLatest()(*)).thenReturn(TermsOfUseVersion.latest)

  def returnTermsOfUseVersionForApplication = when(termsOfUseVersionServiceMock.getForApplication(*[ApplicationWithCollaborators])(*)).thenReturn(TermsOfUseVersion.NEW_JOURNEY)

}
