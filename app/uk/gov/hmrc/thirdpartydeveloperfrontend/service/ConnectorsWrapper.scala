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

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

import com.google.inject.name.Named

import uk.gov.hmrc.apiplatform.modules.common.domain.models._
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ApplicationConfig
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors._
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.PushPullNotificationsService.PushPullNotificationsConnector

@Singleton
class ConnectorsWrapper @Inject() (
    val sandboxApplicationConnector: ThirdPartyApplicationSandboxConnector,
    val productionApplicationConnector: ThirdPartyApplicationProductionConnector,
    @Named("PPNS-SANDBOX") val sandboxPushPullNotificationsConnector: PushPullNotificationsConnector,
    @Named("PPNS-PRODUCTION") val productionPushPullNotificationsConnector: PushPullNotificationsConnector,
    applicationConfig: ApplicationConfig
  )(implicit val ec: ExecutionContext
  ) {

  def forEnvironment(environment: Environment): Connectors = {
    environment match {
      case Environment.PRODUCTION => Connectors(productionApplicationConnector, productionPushPullNotificationsConnector)
      case _                      => Connectors(sandboxApplicationConnector, sandboxPushPullNotificationsConnector)
    }
  }
}

case class Connectors(
    thirdPartyApplicationConnector: ThirdPartyApplicationConnector,
    pushPullNotificationsConnector: PushPullNotificationsConnector
  )
