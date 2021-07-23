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
import service.UpliftDataService
import domain.models.applications.ApplicationId
import scala.concurrent.Future.successful
import domain.models.controllers.SandboxApplicationSummary
import domain.models.developers.UserId

class UpliftDataServiceMock extends MockitoSugar with ArgumentMatchersSugar {
  val upliftDataServiceMock = mock[UpliftDataService]

  def getUpliftDataReturns(summaries : Seq[SandboxApplicationSummary], haveAppsThatCannotBeUplifted: Boolean) =
    when(upliftDataServiceMock.getUpliftData(*[UserId])(*)).thenReturn(successful((summaries, haveAppsThatCannotBeUplifted)))

  def identifyUpliftableSandboxAppIdsReturns(sandboxApplicationIds: Set[ApplicationId]) = 
    when(upliftDataServiceMock.identifyUpliftableSandboxAppIds(*)(*)).thenReturn(successful(sandboxApplicationIds))

}
