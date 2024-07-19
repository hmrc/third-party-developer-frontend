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

package uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.flows

import scala.reflect.runtime.universe._

import cats.Semigroup
import cats.implicits._

import uk.gov.hmrc.apiplatform.modules.apis.domain.models.ServiceName
import uk.gov.hmrc.apiplatform.modules.common.domain.models.ApplicationId
import uk.gov.hmrc.apiplatform.modules.tpd.core.domain.models.SessionId
import uk.gov.hmrc.apiplatform.modules.tpd.emailpreferences.domain.models.{EmailPreferences, EmailTopic, TaxRegimeInterests}
import uk.gov.hmrc.apiplatform.modules.tpd.session.domain.models.{UserSessionId}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.session.DeveloperSession
import uk.gov.hmrc.apiplatform.modules.uplift.domain.models.GetProductionCredentialsFlow
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.SupportSessionId
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors.CombinedApi

sealed trait FlowType

object FlowType {
  case object IP_ALLOW_LIST                        extends FlowType
  case object EMAIL_PREFERENCES                    extends FlowType
  case object EMAIL_PREFERENCES_V2                 extends FlowType
  case object NEW_APPLICATION_EMAIL_PREFERENCES    extends FlowType
  case object NEW_APPLICATION_EMAIL_PREFERENCES_V2 extends FlowType
  case object GET_PRODUCTION_CREDENTIALS           extends FlowType
  case object SUPPORT_FLOW                         extends FlowType

  val values: List[FlowType] = List(
    IP_ALLOW_LIST,
    EMAIL_PREFERENCES,
    EMAIL_PREFERENCES_V2,
    NEW_APPLICATION_EMAIL_PREFERENCES,
    NEW_APPLICATION_EMAIL_PREFERENCES_V2,
    GET_PRODUCTION_CREDENTIALS,
    SUPPORT_FLOW
  )

  def from[A <: Flow: TypeTag]: FlowType = {
    typeOf[A] match {
      case t if t =:= typeOf[EmailPreferencesFlowV2]               => FlowType.EMAIL_PREFERENCES_V2
      case t if t =:= typeOf[IpAllowlistFlow]                      => FlowType.IP_ALLOW_LIST
      case t if t =:= typeOf[NewApplicationEmailPreferencesFlowV2] => FlowType.NEW_APPLICATION_EMAIL_PREFERENCES_V2
      case t if t =:= typeOf[GetProductionCredentialsFlow]         => FlowType.GET_PRODUCTION_CREDENTIALS
      case t if t =:= typeOf[SupportFlow]                          => FlowType.SUPPORT_FLOW
    }
  }

  def apply(text: String): Option[FlowType] = FlowType.values.find(_.toString() == text.toUpperCase)

  import play.api.libs.json.Format
  import uk.gov.hmrc.apiplatform.modules.common.domain.services.SealedTraitJsonFormatting
  implicit val format: Format[FlowType] = SealedTraitJsonFormatting.createFormatFor[FlowType]("Flow Type", FlowType.apply)
}

trait Flow {
  type Type <: SessionId
  def sessionId: Type
  def flowType: FlowType
}

/** The name of the class is used on serialisation as a discriminator. Do not change.
  */
case class IpAllowlistFlow(override val sessionId: UserSessionId, allowlist: Set[String]) extends Flow {
  type Type = UserSessionId
  override val flowType: FlowType = FlowType.IP_ALLOW_LIST
}
case class SupportApi(serviceName: ServiceName, name: String)

case class SupportFlow(
    override val sessionId: SupportSessionId,
    entrySelection: String,
    subSelection: Option[String] = None,
    api: Option[String] = None,
    privateApi: Option[String] = None,
    emailAddress: Option[String] = None,
    referenceNumber: Option[String] = None
  ) extends Flow {

  type Type = SupportSessionId
  override def flowType: FlowType = FlowType.SUPPORT_FLOW
}

case class EmailPreferencesFlowV2(
    override val sessionId: UserSessionId,
    selectedCategories: Set[String],
    selectedAPIs: Map[String, Set[String]],
    selectedTopics: Set[String],
    visibleApis: List[CombinedApi]
  ) extends Flow with EmailPreferencesProducer {

  type Type = UserSessionId

  override val flowType: FlowType = FlowType.EMAIL_PREFERENCES_V2

  def categoriesInOrder: List[String]                            = selectedCategories.toList.sorted
// testng for a string against list of apicatrgories/// amazed it doesn't complain about type - how String <: ApiCategory is beyond me...
  def visibleApisByCategory(category: String): List[CombinedApi] = visibleApis.filter(_.categories.map(_.toString()).contains(category)).sortBy(_.displayName)

  def selectedApisByCategory(category: String): Set[String] = selectedAPIs.getOrElse(category, Set.empty)

  def handleAllApis(apis: Set[String]): Set[String] = {
    if (apis.contains("ALL_APIS")) Set.empty[String] else apis
  }

  override def toEmailPreferences: EmailPreferences = {
    val interests: List[TaxRegimeInterests] =
      selectedAPIs.map(x => TaxRegimeInterests(x._1, handleAllApis(x._2))).toList

    EmailPreferences(interests, selectedTopics.map(EmailTopic.unsafeApply(_)))
  }
}

object EmailPreferencesFlowV2 {

  def fromDeveloperSession(developerSession: DeveloperSession): EmailPreferencesFlowV2 = {
    val existingEmailPreferences = developerSession.developer.emailPreferences

    existingEmailPreferences match {
      case EmailPreferences(i: List[TaxRegimeInterests], t: Set[EmailTopic]) if i.isEmpty && t.isEmpty =>
        new EmailPreferencesFlowV2(developerSession.session.sessionId, Set.empty, Map.empty, Set.empty, List.empty)
      case emailPreferences                                                                            => new EmailPreferencesFlowV2(
          developerSession.session.sessionId,
          emailPreferences.interests.map(_.regime).toSet,
          taxRegimeInterestsToCategoryServicesMap(emailPreferences.interests),
          emailPreferences.topics.map(_.toString),
          List.empty
        )
    }
  }

  def taxRegimeInterestsToCategoryServicesMap(interests: List[TaxRegimeInterests]): Map[String, Set[String]] = {
    interests.map(i => {
      val services = if (i.services.isEmpty) Set("ALL_APIS") else i.services
      (i.regime, services)
    }).toMap
  }
}

case class NewApplicationEmailPreferencesFlowV2(
    override val sessionId: UserSessionId,
    existingEmailPreferences: EmailPreferences,
    applicationId: ApplicationId,
    missingSubscriptions: Set[CombinedApi],
    selectedApis: Set[CombinedApi],
    selectedTopics: Set[String]
  ) extends Flow with EmailPreferencesProducer {

  type Type = UserSessionId
  override val flowType: FlowType = FlowType.NEW_APPLICATION_EMAIL_PREFERENCES_V2

  def optionCombine[A: Semigroup](a: A, opt: Option[A]): A = opt.map(a combine _).getOrElse(a)

  def mergeMap[K, V: Semigroup](lhs: Map[K, V], rhs: Map[K, V]): Map[K, V] =
    lhs.foldLeft(rhs) {
      case (acc, (k, v)) => acc.updated(k, optionCombine(v, acc.get(k)))
    }

  override def toEmailPreferences: EmailPreferences = {
    val existingInterests: Map[String, Set[String]] =
      existingEmailPreferences.interests
        .map(interests => Map(interests.regime -> interests.services))
        .foldLeft(Map.empty[String, Set[String]])(_ ++ _)

    // Map[ServiceName -> Set[Category]]
    val selectedApisCategories: Map[String, Set[String]] = selectedApis.map(api => (api.serviceName.value -> api.categories.map(_.toString()).toSet)).toMap

    // Map[Category -> Set.empty[ServiceName]]
    val invertedSelectedApisCategories: Map[String, Set[String]] = selectedApisCategories.values.flatten.map(c => c -> Set.empty[String]).toMap

    val newInterests: Map[String, Set[String]] = invertedSelectedApisCategories.map(p => {
      val serviceNames    = selectedApisCategories.filter(p2 => p2._2.contains(p._1)).keys
      val newServiceNames = p._2 ++ serviceNames

      p._1 -> newServiceNames
    })

    val combinedInterests: Map[String, Set[String]] = mergeMap(existingInterests, newInterests)

    val updatedTaxRegimeInterests = combinedInterests.map(i => TaxRegimeInterests(i._1, i._2)).toList

    EmailPreferences(updatedTaxRegimeInterests, selectedTopics.map(EmailTopic.unsafeApply(_)))
  }
}

trait EmailPreferencesProducer {
  def toEmailPreferences: EmailPreferences
}
