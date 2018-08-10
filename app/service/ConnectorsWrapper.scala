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

import config.ApplicationConfig
import connectors._
import domain.{Application, ApplicationNotFound}
import domain.Environment.{Environment, PRODUCTION}
import uk.gov.hmrc.http.HeaderCarrier
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait ConnectorsWrapper {

  val productionApplicationConnector: ThirdPartyApplicationConnector
  val sandboxApplicationConnector: ThirdPartyApplicationConnector

  val sandboxSubscriptionFieldsConnector: ApiSubscriptionFieldsConnector
  val productionSubscriptionFieldsConnector: ApiSubscriptionFieldsConnector

  protected val applicationConfig: ApplicationConfig

  def forApplication(applicationId: String)(implicit hc: HeaderCarrier): Future[Connectors] = {
    fetchApplicationById(applicationId).map(application => connectorsForEnvironment(application.deployedTo))
  }

  def connectorsForEnvironment(environment: Environment): Connectors = {
    environment match {
      case PRODUCTION => Connectors(productionApplicationConnector, productionSubscriptionFieldsConnector)
      case _ => Connectors(sandboxApplicationConnector, sandboxSubscriptionFieldsConnector)
    }
  }

  def fetchApplicationById(id: String)(implicit hc: HeaderCarrier): Future[Application] = {
    if (applicationConfig.strategicSandboxEnabled) {
      val productionApplicationFuture = productionApplicationConnector.fetchApplicationById(id)
      val sandboxApplicationFuture = sandboxApplicationConnector.fetchApplicationById(id) recover {
        case _ =>  None
      }

      for {
        productionApplication <- productionApplicationFuture
        sandboxApplication <- sandboxApplicationFuture
      } yield {
        productionApplication.orElse(sandboxApplication).getOrElse(throw new ApplicationNotFound)
      }
    }
    else {
      productionApplicationConnector.fetchApplicationById(id).map(_.getOrElse(throw new ApplicationNotFound))
    }
  }

}

object ConnectorsWrapper extends ConnectorsWrapper{
  override val productionApplicationConnector = ThirdPartyApplicationProductionConnector
  override val sandboxApplicationConnector = ThirdPartyApplicationSandboxConnector
  override val sandboxSubscriptionFieldsConnector: ApiSubscriptionFieldsConnector = ApiSubscriptionFieldsSandboxConnector
  override val productionSubscriptionFieldsConnector: ApiSubscriptionFieldsConnector = ApiSubscriptionFieldsProductionConnector
  override val applicationConfig = ApplicationConfig
}

case class Connectors(thirdPartyApplicationConnector: ThirdPartyApplicationConnector, apiSubscriptionFieldsConnector: ApiSubscriptionFieldsConnector)
