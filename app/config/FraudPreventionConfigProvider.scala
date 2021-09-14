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

import play.api.Configuration

import javax.inject.Inject
import javax.inject.Singleton
import scala.collection.JavaConverters._
import com.google.inject.Provider

case class FraudPreventionConfig(enabled: Boolean, apisWithFraudPrevention: List[String], uri: String)

@Singleton
class FraudPreventionConfigProvider @Inject()(config: Configuration) extends Provider[FraudPreventionConfig] {

    override def get(): FraudPreventionConfig = {

      val enabled:Boolean = config.underlying.getBoolean("fraudPreventionLink.enabled")
      val apisWithFraudPrevention: List[String] = config.underlying.getStringList("fraudPreventionLink.apisWithFraudPrevention").asScala.toList
      val uri: String = config.underlying.getString("fraudPreventionLink.uri")

      val result = FraudPreventionConfig(enabled, apisWithFraudPrevention, uri)
      result
    }
}