/*
 * Copyright 2019 HM Revenue & Customs
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
import domain.Environment.PRODUCTION
import domain._
import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ConnectorsWrapper @Inject()(val sandboxApplicationConnector: ThirdPartyApplicationSandboxConnector,
                                  val productionApplicationConnector: ThirdPartyApplicationProductionConnector,
                                  val sandboxSubscriptionFieldsConnector: ApiSubscriptionFieldsSandboxConnector,
                                  val productionSubscriptionFieldsConnector: ApiSubscriptionFieldsProductionConnector,
                                  applicationConfig: ApplicationConfig)(implicit val ec: ExecutionContext) {

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
        case _ => None
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

case class Connectors(thirdPartyApplicationConnector: ThirdPartyApplicationConnector, apiSubscriptionFieldsConnector: ApiSubscriptionFieldsConnector)
