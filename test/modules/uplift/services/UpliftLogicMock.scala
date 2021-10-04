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

package modules.uplift.services


import org.mockito.MockitoSugar
import org.mockito.ArgumentMatchersSugar
import domain.models.developers.UserId
import scala.concurrent.Future._
import domain.models.controllers.ApplicationSummary
import domain.models.applications.ApplicationId

class UpliftLogicMock extends MockitoSugar with ArgumentMatchersSugar {

  val upliftLogicMock = mock[UpliftLogic]

  def aUsersUplfitableAndNotUpliftableAppsReturns(summaries : List[ApplicationSummary], upliftableAppIds : List[ApplicationId]) = {
      when(upliftLogicMock.aUsersSandboxAdminSummariesAndUpliftIds(*[UserId])(*)).thenReturn(successful((summaries, upliftableAppIds.toSet)))
  }
}

