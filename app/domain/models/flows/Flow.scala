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

import domain.models.connectors.ApiDefinition
import domain.models.developers.DeveloperSession
import domain.models.emailpreferences.{EmailPreferences, EmailTopic, TaxRegimeInterests}
import enumeratum.{Enum, EnumEntry, PlayJsonEnum}

import scala.collection.immutable

sealed trait FlowType extends EnumEntry

object FlowType extends Enum[FlowType] with PlayJsonEnum[FlowType] {
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
  override val flowType: FlowType = FlowType.IP_ALLOW_LIST
}

case class EmailPreferencesFlow(override val sessionId: String,
                                selectedCategories: Set[String],
                                selectedAPIs: Map[String, Set[String]],
                                selectedTopics: Set[String],
                                visibleApis: Seq[ApiDefinition]) extends Flow {
  override val flowType: FlowType = FlowType.EMAIL_PREFERENCES

  def categoriesInOrder: List[String] = selectedCategories.toList.sorted

  def visibleApisByCategory(category: String): List[ApiDefinition] = visibleApis.filter(_.categories.contains(category)).toList.sortBy(_.name)

  def selectedApisByCategory(category: String): Set[String] = selectedAPIs.getOrElse(category, Set.empty)

  def handleAllApis(apis: Set[String]): Set[String] = {
    if (apis.contains("ALL_APIS")) Set.empty[String] else apis
  }

  def toEmailPreferences: EmailPreferences = {
    val interests: List[TaxRegimeInterests] =
      selectedAPIs.map(x => TaxRegimeInterests(x._1, handleAllApis(x._2))).toList

    EmailPreferences(interests, selectedTopics.map(EmailTopic.withValue))
  }
}

object EmailPreferencesFlow {
  def fromDeveloperSession(developerSession: DeveloperSession): EmailPreferencesFlow = {

    val existingEmailPreferences = developerSession.developer.emailPreferences
    existingEmailPreferences match {
      case EmailPreferences(i: List[TaxRegimeInterests], t: Set[EmailTopic]) if i.isEmpty && t.isEmpty =>
        new EmailPreferencesFlow(developerSession.session.sessionId, Set.empty, Map.empty, Set.empty, Seq.empty)
      case emailPreferences => new EmailPreferencesFlow(
        developerSession.session.sessionId,
        emailPreferences.interests.map(_.regime).toSet,
        taxRegimeInterestsToCategoryServicesMap(emailPreferences.interests),
        emailPreferences.topics.map(_.value),
        Seq.empty)
    }
  }

  def taxRegimeInterestsToCategoryServicesMap(interests: List[TaxRegimeInterests]): Map[String, Set[String]] = {
    interests.map(i => {
      val services = if (i.services.isEmpty) Set("ALL_APIS") else i.services
      (i.regime, services)
    }).toMap
  }
}


