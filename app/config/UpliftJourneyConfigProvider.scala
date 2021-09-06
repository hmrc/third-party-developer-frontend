/*
 * Copyright 2021 HM Revenue & Customs
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

import javax.inject.{Inject, Singleton}
import play.api.Configuration

sealed trait NewJourneyFeature
final case object Off extends NewJourneyFeature
final case object On extends NewJourneyFeature
final case object OnDemand extends NewJourneyFeature

@Singleton
class UpliftJourneyConfigProvider @Inject()(configuration: Configuration) {

    private def configNotFoundError(key: String) =
        throw new RuntimeException(s"Could not find config key '$key'")

    private def getString(key: String) =
        configuration.getOptional[String](key).getOrElse(configNotFoundError(key))

    def status: NewJourneyFeature = 
        getString("applicationCheck.canUseNewUpliftJourney") match {
            case "On" => On
            case "OnDemand" => OnDemand
            case _ => Off
        }
}