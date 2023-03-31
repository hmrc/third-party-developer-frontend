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

import uk.gov.hmrc.thirdpartydeveloperfrontend.service.RedirectsService
import scala.concurrent.Future.successful
import org.mockito.ArgumentMatchersSugar
import org.mockito.MockitoSugar
import uk.gov.hmrc.apiplatform.modules.commands.applications.domain.models.DispatchSuccessResult

trait RedirectsServiceMockModule extends MockitoSugar with ArgumentMatchersSugar {

  trait AbstractRedirectsServiceMock {
    def aMock: RedirectsService

    val mockDispatchSuccessResult = mock[DispatchSuccessResult]

    object AddRedirect {
      def succeedsWith(uri: String) = {
        when(aMock.addRedirect(*,*,eqTo(uri))(*)).thenReturn(successful(Right(mockDispatchSuccessResult)))
      }
    }

    object ChangeRedirect {
      def succeedsWith(from: String, to: String) = {
        when(aMock.changeRedirect(*,*,eqTo(from),eqTo(to))(*)).thenReturn(successful(Right(mockDispatchSuccessResult)))
      }
    }

    object DeleteRedirect {
      def succeedsWith(uri: String) = {
        when(aMock.deleteRedirect(*,*,eqTo(uri))(*)).thenReturn(successful(Right(mockDispatchSuccessResult)))
      }
    }
  }

  object RedirectsServiceMock extends AbstractRedirectsServiceMock {
    val aMock = mock[RedirectsService]
  }
}
