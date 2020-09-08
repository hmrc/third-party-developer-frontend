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

package connectors

import connectors.PushPullNotificationsConnector.readsPushSecret
import domain.models.applications.ClientId
import javax.inject.{Inject, Singleton}
import play.api.libs.json.{Json, Reads}
import uk.gov.hmrc.http.logging.Authorization
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads, NotFoundException}
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.play.http.metrics.API

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class PushPullNotificationsConnector @Inject()(http: HttpClient, config: PushPullNotificationsConnector.Config)(implicit ec: ExecutionContext) {
  val api: API = API("push-pull-notifications-api")

  def fetchPushSecrets(clientId: ClientId)(implicit hc: HeaderCarrier): Future[Seq[String]] = {
    getWithAuthorization[Seq[PushSecret]](s"${config.serviceBaseUrl}/client/${clientId.value}/secrets", hc).map(_.map(_.value)) recover {
      case _: NotFoundException => Seq.empty
    }
  }

  private def getWithAuthorization[A](url:String, hc: HeaderCarrier)(implicit rd: HttpReads[A]): Future[A] = {
    implicit val modifiedHeaderCarrier: HeaderCarrier = hc.copy(authorization = Some(Authorization(config.authorizationKey)))
    http.GET[A](url)
  }
}

object PushPullNotificationsConnector {
  case class Config(serviceBaseUrl: String, authorizationKey: String)
  implicit val readsPushSecret: Reads[PushSecret] = Json.reads[PushSecret]
}

private[connectors] case class PushSecret(value: String)
