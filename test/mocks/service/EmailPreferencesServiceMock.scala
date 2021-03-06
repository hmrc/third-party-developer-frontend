/*
 * Copyright 2021 HM Revenue & Customs
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

import org.mockito.{ArgumentMatchersSugar, MockitoSugar}
import service.EmailPreferencesService

import scala.concurrent.Future.successful
import domain.models.connectors.ExtendedApiDefinition

trait EmailPreferencesServiceMock extends MockitoSugar with ArgumentMatchersSugar {
  val emailPreferencesServiceMock = mock[EmailPreferencesService]

  def fetchAPIDetailsReturns(apis: List[ExtendedApiDefinition]) = {
    when(emailPreferencesServiceMock.fetchAPIDetails(*)(*)).thenReturn(successful(apis))
  }
}