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

package uk.gov.hmrc.thirdpartydeveloperfrontend.connectors

import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.Environment
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.Environment._
import com.google.inject.Inject
import com.google.inject.name.Named
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.Application


case class BridgedConnector[T] @Inject() (@Named("SANDBOX") sandbox: T, @Named("PRODUCTION") production: T) {

  def forEnvironment(environment: Environment): T = {
    environment match {
      case PRODUCTION => production
      case _          => sandbox
    }
  }

  def apply(application: Application): T = {
    forEnvironment(application.deployedTo)
  }
}
