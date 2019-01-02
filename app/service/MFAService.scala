/*
 * Copyright 2018 HM Revenue & Customs
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

package service

import connectors.ThirdPartyDeveloperConnector
import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.Future.successful

@Singleton
class MFAService @Inject()(tpdConnector: ThirdPartyDeveloperConnector) {

  def enableMfa(email: String, totpCode: String)(implicit hc: HeaderCarrier): Future[MFAResponse] = {
    tpdConnector.verifyMfa(email, totpCode) flatMap {
      case true => tpdConnector.enableMfa(email).map(_ => MFAResponse(true))
      case _ => successful(MFAResponse(false))
    }
  }

  def removeMfa(email: String, totpCode: String)(implicit hc: HeaderCarrier): Future[MFAResponse] = {
    tpdConnector.verifyMfa(email, totpCode) flatMap {
      case true => tpdConnector.removeMfa(email).map(_ => MFAResponse(true))
      case _ => successful(MFAResponse(false))
    }
  }
}

case class MFAResponse(totpVerified: Boolean)
