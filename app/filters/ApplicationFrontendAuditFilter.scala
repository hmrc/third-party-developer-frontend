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

package filters

import akka.stream.Materializer
import javax.inject.{Inject, Named}
import play.api.Configuration
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.bootstrap.config.{ControllerConfigs, HttpAuditEvent}
import uk.gov.hmrc.play.bootstrap.filters.frontend.DefaultFrontendAuditFilter

import scala.concurrent.ExecutionContext

class ApplicationFrontendAuditFilter @Inject()(
                                            val configuration: Configuration,
                                            controllerConfigs: ControllerConfigs,
                                            override val auditConnector: AuditConnector,
                                            auditEvent: HttpAuditEvent,
                                            override val mat: Materializer,
                                            @Named("appName") appName: String
                                          )(override implicit val ec: ExecutionContext)
  extends DefaultFrontendAuditFilter(controllerConfigs, auditConnector, auditEvent, mat) {

  override def controllerNeedsAuditing(controllerName: String): Boolean =
    controllerConfigs.get(controllerName).auditing

  override val maskedFormFields: Seq[String] = Seq("password")

  override val applicationPort: Option[Int] = None
}
