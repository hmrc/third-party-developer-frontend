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

package uk.gov.hmrc.thirdpartydeveloperfrontend.config

import org.apache.pekko.pattern.FutureTimeoutSupport

import play.api.inject.Module
import play.api.{Configuration, Environment}

import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.ClientSecretsHashingConfig
import uk.gov.hmrc.apiplatform.modules.submissions.config.ThirdPartyApplicationSubmissionsConnectorConfigProvider
import uk.gov.hmrc.apiplatform.modules.submissions.connectors.ThirdPartyApplicationSubmissionsConnector
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors._
import uk.gov.hmrc.thirdpartydeveloperfrontend.helpers.FutureTimeoutSupportImpl

// This understands types such as Traits
class ScalaBasedConfigurationModule extends Module {

  override def bindings(environment: Environment, configuration: Configuration) = {
    Seq(
      bind[ConnectorMetrics].to[ConnectorMetricsImpl],
      bind[FutureTimeoutSupport].to[FutureTimeoutSupportImpl],
      bind[ApmConnector.Config].toProvider[LiveApmConnectorConfigProvider],
      bind[FraudPreventionConfig].toProvider[FraudPreventionConfigProvider],
      bind[ThirdPartyApplicationSubmissionsConnector.Config].toProvider[ThirdPartyApplicationSubmissionsConnectorConfigProvider],
      bind[ApiPlatformDeskproConnector.Config].toProvider[ApiPlatformDeskproConnectorConfigProvider],
      bind[ClientSecretsHashingConfig].toProvider[ClientSecretsHashingConfigProvider],
      bind[ApmConnectorApiDefinitionModule].to[ApmConnector],
      bind[ApmConnectorSubscriptionFieldsModule].to[ApmConnector],
      bind[ApmConnectorApplicationModule].to[ApmConnector],
      bind[ApmConnectorCombinedApisModule].to[ApmConnector]
    )
  }
}
