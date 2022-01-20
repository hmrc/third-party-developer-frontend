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


import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.ApmConnector
import com.google.inject.{Provider, Inject, Singleton}

@Singleton
class LiveApmConnectorConfigProvider @Inject() (config: ServicesConfig) extends Provider[ApmConnector.Config] {

  override def get(): ApmConnector.Config =
    ApmConnector.Config(
      serviceBaseUrl = config.baseUrl("api-platform-microservice")
    )
}
