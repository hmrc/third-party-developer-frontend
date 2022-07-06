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

package uk.gov.hmrc.thirdpartydeveloperfrontend.config

import akka.pattern.FutureTimeoutSupport
import com.google.inject.AbstractModule
import com.google.inject.name.Names.named
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors._
import uk.gov.hmrc.thirdpartydeveloperfrontend.helpers.FutureTimeoutSupportImpl
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.PushPullNotificationsService.PushPullNotificationsConnector
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.SubscriptionFieldsService.SubscriptionFieldsConnector
import uk.gov.hmrc.play.bootstrap.filters.frontend.SessionTimeoutFilter
import uk.gov.hmrc.apiplatform.modules.submissions.connectors.ThirdPartyApplicationSubmissionsConnector
import uk.gov.hmrc.apiplatform.modules.submissions.config.ThirdPartyApplicationSubmissionsConnectorConfigProvider

import java.time.Clock

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

    bind(classOf[FraudPreventionConfig])
      .toProvider(classOf[FraudPreventionConfigProvider])

    bind(classOf[ThirdPartyApplicationSubmissionsConnector.Config])
      .toProvider(classOf[ThirdPartyApplicationSubmissionsConnectorConfigProvider])

    bind(classOf[Clock]).toInstance(Clock.systemUTC())
  }
}
