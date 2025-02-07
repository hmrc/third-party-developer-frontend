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

import scala.concurrent.Future.successful

import org.mockito.{ArgumentMatchersSugar, MockitoSugar}

import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.LoginRedirectUri
import uk.gov.hmrc.apiplatform.modules.commands.applications.domain.models.DispatchSuccessResult
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.LoginRedirectsService

trait RedirectsServiceMockModule extends MockitoSugar with ArgumentMatchersSugar {

  trait AbstractRedirectsServiceMock {
    def aMock: LoginRedirectsService

    val mockDispatchSuccessResult: DispatchSuccessResult = mock[DispatchSuccessResult]

    object AddLoginRedirect {

      def succeedsWith(uri: LoginRedirectUri) = {
        when(aMock.addLoginRedirect(*, *, eqTo(uri))(*)).thenReturn(successful(Right(mockDispatchSuccessResult)))
      }
    }

    object ChangeLoginRedirect {

      def succeedsWith(from: LoginRedirectUri, to: LoginRedirectUri) = {
        when(aMock.changeLoginRedirect(*, *, eqTo(from), eqTo(to))(*)).thenReturn(successful(Right(mockDispatchSuccessResult)))
      }
    }

    object DeleteLoginRedirect {

      def succeedsWith(uri: LoginRedirectUri) = {
        when(aMock.deleteLoginRedirect(*, *, eqTo(uri))(*)).thenReturn(successful(Right(mockDispatchSuccessResult)))
      }
    }
  }

  object RedirectsServiceMock extends AbstractRedirectsServiceMock {
    val aMock = mock[LoginRedirectsService]
  }
}
