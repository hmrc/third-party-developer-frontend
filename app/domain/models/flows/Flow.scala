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

package domain.models.flows

import model.EmailTopic
import enumeratum.{Enum, EnumEntry, PlayJsonEnum}
import scala.collection.immutable

sealed trait FlowType extends EnumEntry

object FlowType extends  Enum[FlowType] with PlayJsonEnum[FlowType]  {
  val values: immutable.IndexedSeq[FlowType] = findValues

  case object IP_ALLOW_LIST extends FlowType
  case object EMAIL_PREFERENCES extends FlowType

}

trait Flow {
  val sessionId: String
  val flowType: FlowType
}


/**
 * The name of the class is used on serialisation as a discriminator. Do not change.
 */
case class IpAllowlistFlow(override val sessionId: String,                       
                           allowlist: Set[String]) extends Flow {
                              override val flowType = FlowType.IP_ALLOW_LIST
                           }

case class EmailPreferencesFlow(override val sessionId: String,
                           selectedTopics: Set[EmailTopic]) extends Flow {
                             override val flowType = FlowType.EMAIL_PREFERENCES
                           }