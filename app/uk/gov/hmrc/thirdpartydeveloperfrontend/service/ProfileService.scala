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

package uk.gov.hmrc.thirdpartydeveloperfrontend.service

import java.time.Clock
import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apiplatform.modules.common.domain.models._
import uk.gov.hmrc.apiplatform.modules.common.services.ClockNow
import uk.gov.hmrc.apiplatform.modules.tpd.core.dto.UpdateRequest
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors._

@Singleton
class ProfileService @Inject() (
    deskproConnector: ApiPlatformDeskproConnector,
    developerConnector: ThirdPartyDeveloperConnector,
    val clock: Clock
  )(implicit val ec: ExecutionContext
  ) extends ClockNow {

  def updateProfileName(userId: UserId, email: LaxEmailAddress, firstName: String, lastName: String)(implicit hc: HeaderCarrier) = {
    val name = firstName + " " + lastName
    for {
      response <- developerConnector.updateProfile(userId, UpdateRequest(firstName, lastName))
      result   <- deskproConnector.updatePersonName(email, name, hc)
    } yield response
  }

}
