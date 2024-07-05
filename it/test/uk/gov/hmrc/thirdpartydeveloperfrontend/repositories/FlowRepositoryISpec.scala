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

package uk.gov.hmrc.thirdpartydeveloperfrontend.repositories

import java.time.Instant
import scala.concurrent.ExecutionContext.Implicits.global

import org.mongodb.scala.bson.{BsonValue, Document}
import org.mongodb.scala.model.Aggregates.{filter, project}
import org.mongodb.scala.model.Projections.fields
import org.mongodb.scala.model.{Filters, Projections}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.{BeforeAndAfterEach, OptionValues}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite

import play.api.libs.json.{Format, OFormat}
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import uk.gov.hmrc.mongo.play.json.Codecs
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats

import uk.gov.hmrc.apiplatform.modules.apis.domain.models.{ApiCategory, ServiceName}
import uk.gov.hmrc.apiplatform.modules.applications.access.domain.models.SellResellOrDistribute
import uk.gov.hmrc.apiplatform.modules.common.domain.models.{ApiContext, ApiIdentifier, ApiVersionNbr}
import uk.gov.hmrc.apiplatform.modules.emailpreferences.domain.models.EmailTopic
import uk.gov.hmrc.apiplatform.modules.uplift.domain.models.{ApiSubscriptions, GetProductionCredentialsFlow}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors.ApiType.REST_API
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors.CombinedApi
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.flows.FlowType._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.flows.{EmailPreferencesFlowV2, Flow, FlowType, IpAllowlistFlow}

class FlowRepositoryISpec extends AnyWordSpec
    with GuiceOneAppPerSuite
    with Matchers
    with OptionValues
    with DefaultAwaitTimeout
    with FutureAwaits
    with BeforeAndAfterEach {

  private val currentSession = "session 1"
  private val anotherSession = "session 2"

  private val flowRepository = app.injector.instanceOf[FlowRepository]

  override protected def beforeEach(): Unit = {
    await(flowRepository.collection.drop().toFuture())
    await(flowRepository.ensureIndexes())
  }

  trait PopulatedSetup {
    val currentFlow: IpAllowlistFlow            = IpAllowlistFlow(currentSession, Set("ip1", "ip2"))
    val flowInDifferentSession: IpAllowlistFlow = IpAllowlistFlow(anotherSession, Set("ip3", "ip4"))

    val flowOfDifferentType: EmailPreferencesFlowV2 = EmailPreferencesFlowV2(
      currentSession,
      selectedCategories = Set(ApiCategory.VAT.toString(), ApiCategory.AGENTS.toString()),
      selectedAPIs = Map(ApiCategory.VAT.toString() -> Set("qwqw", "asass")),
      selectedTopics = Set("BUSINESS_AND_POLICY"),
      visibleApis = List(CombinedApi(ServiceName("api1ServiceName"), "api1Name", List(ApiCategory.VAT, ApiCategory.AGENTS), REST_API))
    )

    await(flowRepository.saveFlow(currentFlow))
    await(flowRepository.saveFlow(flowInDifferentSession))
    await(flowRepository.saveFlow(flowOfDifferentType))
    await(flowRepository.collection.countDocuments().toFuture()) shouldBe 3

    def fetchLastUpdated(flow: Flow): Instant = {
      val query = Document("sessionId" -> Codecs.toBson(flow.sessionId), "flowType" -> Codecs.toBson(flow.flowType))

      await(flowRepository.collection.aggregate[BsonValue](
        Seq(
          filter(query),
          project(fields(Projections.excludeId(), Projections.include("lastUpdated")))
        )
      ).head()
        .map(Codecs.fromBson[ResultSet]))
        .lastUpdated
    }
  }

  "FlowRepository" when {

    "saveFlow" should {
      "save IP allowlist" in {
        val flow = IpAllowlistFlow(currentSession, Set("ip1", "ip2"))

        await(flowRepository.saveFlow(flow))

        val result = await(flowRepository.collection.find(Filters.equal("sessionId", currentSession)).headOption())
        result match {
          case Some(savedFlow: IpAllowlistFlow) =>
            savedFlow.sessionId shouldBe currentSession
            savedFlow.flowType shouldBe IP_ALLOW_LIST
            savedFlow.allowlist shouldBe Set("ip1", "ip2")
          case _                                => fail()
        }

      }

      "save email preferences" in {
        val flow = EmailPreferencesFlowV2(
          currentSession,
          selectedCategories = Set(ApiCategory.VAT.toString(), ApiCategory.AGENTS.toString()),
          selectedAPIs = Map(ApiCategory.VAT.toString() -> Set("qwqw", "asass")),
          selectedTopics = Set("BUSINESS_AND_POLICY", "EVENT_INVITES"),
          visibleApis = List(CombinedApi(ServiceName("api1ServiceName"), "api1DisplayName", List(ApiCategory.VAT, ApiCategory.AGENTS), REST_API))
        )

        await(flowRepository.saveFlow(flow))

        val result: Flow = await(flowRepository.collection.find(Filters.equal("sessionId", Codecs.toBson(currentSession))).first().toFuture())
        val castResult   = result.asInstanceOf[EmailPreferencesFlowV2]
        castResult.sessionId shouldBe currentSession
        castResult.flowType shouldBe EMAIL_PREFERENCES_V2
        castResult.selectedTopics shouldBe Set(EmailTopic.BUSINESS_AND_POLICY.toString, EmailTopic.EVENT_INVITES.toString)
        castResult.visibleApis should contain only (CombinedApi(ServiceName("api1ServiceName"), "api1DisplayName", List(ApiCategory.VAT, ApiCategory.AGENTS), REST_API))
      }

      "save prod creds preferences" in {
        val flow = GetProductionCredentialsFlow(
          currentSession,
          Some(SellResellOrDistribute("Yes")),
          Some(ApiSubscriptions(Map(
            ApiIdentifier(ApiContext("individual-benefits"), ApiVersionNbr("1.1")) -> true,
            ApiIdentifier(ApiContext("marriage-allowance"), ApiVersionNbr("1.0"))  -> true
          )))
        )

        await(flowRepository.saveFlow(flow))

        val result: Flow = await(flowRepository.collection.find(Filters.equal("sessionId", Codecs.toBson(currentSession))).first().toFuture())
        val castResult   = result.asInstanceOf[GetProductionCredentialsFlow]
        castResult.sessionId shouldBe currentSession
        castResult.flowType shouldBe GET_PRODUCTION_CREDENTIALS
        castResult.sellResellOrDistribute shouldBe Some(SellResellOrDistribute("Yes"))
        castResult.apiSubscriptions shouldBe Some(ApiSubscriptions(Map(
          ApiIdentifier(ApiContext("individual-benefits"), ApiVersionNbr("1.1")) -> true,
          ApiIdentifier(ApiContext("marriage-allowance"), ApiVersionNbr("1.0"))  -> true
        )))
      }

      "update the flow when it already exists" in new PopulatedSetup {
        await(flowRepository.saveFlow(currentFlow))
        val updatedFlow: IpAllowlistFlow = currentFlow.copy(allowlist = Set("new IP"))

        val result: IpAllowlistFlow = await(flowRepository.saveFlow(updatedFlow))

        result shouldBe updatedFlow
        val updatedDocument: IpAllowlistFlow = await(flowRepository.collection
          .find(Document("sessionId" -> currentSession, "flowType" -> FlowType.IP_ALLOW_LIST.toString)).map(_.asInstanceOf[IpAllowlistFlow]).head())

        updatedDocument.allowlist shouldBe Set("new IP")
      }
    }

    "deleteBySessionId" should {
      "delete only the flow for the specified session ID and flow type" in new PopulatedSetup {
        val result: Boolean = await(flowRepository.deleteBySessionIdAndFlowType(currentSession, FlowType.IP_ALLOW_LIST))

        result shouldBe true
        await(flowRepository.collection.countDocuments().toFuture()) shouldBe 2
        await(flowRepository.fetchBySessionIdAndFlowType[IpAllowlistFlow](currentSession)) shouldBe None
      }

      "return false if it did not have anything to delete" in {
        val result: Boolean = await(flowRepository.deleteBySessionIdAndFlowType("session 1", FlowType.IP_ALLOW_LIST))

        result shouldBe true
      }
    }

    "fetchBySessionIdAndFlowType" should {

      "fetch the flow for the specified session ID and flow type for IpAllowListFlow" in new PopulatedSetup {

        val result = await(flowRepository.fetchBySessionIdAndFlowType[IpAllowlistFlow](currentSession))

        result shouldBe Some(currentFlow)
      }

      "fetch the flow for the specified session ID and flow type for EmailPreferencesFlowV2" in new PopulatedSetup {

        val result = await(flowRepository.fetchBySessionIdAndFlowType[EmailPreferencesFlowV2](currentSession))

        result shouldBe Some(flowOfDifferentType)
      }

      "return None when the query does not match any data" in {
        val result = await(flowRepository.fetchBySessionIdAndFlowType[IpAllowlistFlow]("session 1"))

        result shouldBe None
      }
    }

    "updateLastUpdated" should {
      "update lastUpdated for all flows for the specified session ID" in new PopulatedSetup {
        val lastUpdatedInCurrentFlow: Instant            = fetchLastUpdated(currentFlow)
        val lastUpdatedInFlowOfDifferentType: Instant    = fetchLastUpdated(flowOfDifferentType)
        val lastUpdatedInFlowInDifferentSession: Instant = fetchLastUpdated(flowInDifferentSession)

        await(flowRepository.updateLastUpdated(currentSession))

        fetchLastUpdated(currentFlow).isAfter(lastUpdatedInCurrentFlow) shouldBe true
        fetchLastUpdated(flowOfDifferentType).isAfter(lastUpdatedInFlowOfDifferentType) shouldBe true
        fetchLastUpdated(flowInDifferentSession) shouldBe lastUpdatedInFlowInDifferentSession
      }
    }
  }
}

case class ResultSet(lastUpdated: Instant)

object ResultSet {
  import play.api.libs.json.Json
  implicit val dateFormat: Format[Instant]         = MongoJavatimeFormats.instantFormat
  implicit val resultSetFormat: OFormat[ResultSet] = Json.format[ResultSet]

  def apply(lastUpdated: Instant) = new ResultSet(lastUpdated)
}
