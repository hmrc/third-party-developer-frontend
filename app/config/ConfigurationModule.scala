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

package config

import akka.pattern.FutureTimeoutSupport
import com.google.inject.AbstractModule
import com.google.inject.name.Names.named
import connectors._
import helpers.FutureTimeoutSupportImpl
import service.PushPullNotificationsService.PushPullNotificationsConnector
import service.SubscriptionFieldsService.SubscriptionFieldsConnector
import uk.gov.hmrc.play.bootstrap.filters.frontend.SessionTimeoutFilter

class ConfigurationModule extends AbstractModule {
  override def configure(): Unit = {
    bind(classOf[ConnectorMetrics]).to(classOf[ConnectorMetricsImpl])
    bind(classOf[SessionTimeoutFilter]).to(classOf[SessionTimeoutFilterWithWhitelist])
    bind(classOf[FutureTimeoutSupport]).to(classOf[FutureTimeoutSupportImpl])

    bind(classOf[SubscriptionFieldsConnector])
      .annotatedWith(named("SANDBOX"))
      .to(classOf[SandboxSubscriptionFieldsConnector])
    bind(classOf[SubscriptionFieldsConnector])
      .annotatedWith(named("PRODUCTION"))
      .to(classOf[ProductionSubscriptionFieldsConnector])

    bind(classOf[PushPullNotificationsConnector])
      .annotatedWith(named("PPNS-SANDBOX"))
      .to(classOf[SandboxPushPullNotificationsConnector])
    bind(classOf[PushPullNotificationsConnector])
      .annotatedWith(named("PPNS-PRODUCTION"))
      .to(classOf[ProductionPushPullNotificationsConnector])

    bind(classOf[ApmConnector.Config])
      .toProvider(classOf[LiveApmConnectorConfigProvider])
  }
}
