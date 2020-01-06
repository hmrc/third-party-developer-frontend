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
import connectors.{ConnectorMetrics, ConnectorMetricsImpl}
import helpers.FutureTimeoutSupportImpl
import uk.gov.hmrc.play.bootstrap.filters.frontend.SessionTimeoutFilter

class ConfigurationModule extends AbstractModule {
  override def configure(): Unit = {
    bind(classOf[ConnectorMetrics]).to(classOf[ConnectorMetricsImpl])
    bind(classOf[SessionTimeoutFilter]).to(classOf[SessionTimeoutFilterWithWhitelist])
    bind(classOf[FutureTimeoutSupport]).to(classOf[FutureTimeoutSupportImpl])
  }
}
