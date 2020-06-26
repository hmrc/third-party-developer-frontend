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

package mocks.service

import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito.when
import org.scalatest.mockito.MockitoSugar
import config.{ApplicationConfig, ErrorHandler}
import play.twirl.api.Html

trait ErrorHandlerMock extends MockitoSugar {
  val mockErrorHandler = mock[ErrorHandler]
  when(mockErrorHandler.notFoundTemplate(any())).thenReturn(Html(""))
  when(mockErrorHandler.badRequestTemplate(any())).thenReturn(Html(""))
  when(mockErrorHandler.forbiddenTemplate(any())).thenReturn(Html(""))
}
