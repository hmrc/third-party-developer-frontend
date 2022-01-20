/*
 * Copyright 2022 HM Revenue & Customs
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

import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.ThirdPartyDeveloperConnector
import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.Future.successful
import domain.models.developers.UserId

@Singleton
class MFAService @Inject()(tpdConnector: ThirdPartyDeveloperConnector)(implicit val ec: ExecutionContext) {

  def enableMfa(userId: UserId, totpCode: String)(implicit hc: HeaderCarrier): Future[MFAResponse] = {
    tpdConnector.verifyMfa(userId, totpCode) flatMap {
      case true => tpdConnector.enableMfa(userId).map(_ => MFAResponse(true))
      case _ => successful(MFAResponse(false))
    }
  }

  def removeMfa(userId: UserId, email: String, totpCode: String)(implicit hc: HeaderCarrier): Future[MFAResponse] = {
    tpdConnector.verifyMfa(userId, totpCode) flatMap {
      case true => tpdConnector.removeMfa(userId, email).map(_ => MFAResponse(true))
      case _ => successful(MFAResponse(false))
    }
  }
}

case class MFAResponse(totpVerified: Boolean)
