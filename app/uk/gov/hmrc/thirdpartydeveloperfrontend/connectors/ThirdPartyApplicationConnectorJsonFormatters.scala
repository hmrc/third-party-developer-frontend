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

package uk.gov.hmrc.thirdpartydeveloperfrontend.connectors

import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.ThirdPartyApplicationConnectorDomain.{AddClientSecretResponse, TPAClientSecret, UpdateIpAllowlistRequest}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.services.{ApiDefinitionsJsonFormatters, SubscriptionsJsonFormatters}
import play.api.libs.json.{Format, Json}

private[connectors] object ThirdPartyApplicationConnectorJsonFormatters
    extends SubscriptionsJsonFormatters
    with ApiDefinitionsJsonFormatters {

  implicit val formatTPAClientSecret: Format[TPAClientSecret] = Json.format[TPAClientSecret]
  implicit val formatAddClientSecretResponse: Format[AddClientSecretResponse] = Json.format[AddClientSecretResponse]
  implicit val formatUpdateIpAllowlistRequest: Format[UpdateIpAllowlistRequest] = Json.format[UpdateIpAllowlistRequest]
}
