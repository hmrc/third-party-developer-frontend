package uk.gov.hmrc.thirdpartydeveloperfrontend.repositories

import akka.stream.Materializer
import org.mongodb.scala.bson.{BsonDocument, BsonValue, Document}
import org.mongodb.scala.model.Aggregates.{filter, project}
import org.mongodb.scala.model.{Aggregates, Filters, Projections}
import org.mongodb.scala.model.Filters._
import org.mongodb.scala.model.Projections.fields
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, OptionValues}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors.ApiType.REST_API
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors.{CombinedApi, CombinedApiCategory}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.emailpreferences.EmailTopic
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.flows.FlowType._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.flows.{EmailPreferencesFlowV2, Flow, FlowType, IpAllowlistFlow}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.libs.json.{Format, OFormat}
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import uk.gov.hmrc.mongo.play.json.Codecs
import uk.gov.hmrc.thirdpartydeveloperfrontend.repositories.MongoFormatters._

import java.time.LocalDateTime
import scala.concurrent.ExecutionContext.Implicits.global
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats

class FlowRepositoryISpec extends AnyWordSpec
  with GuiceOneAppPerSuite
  with Matchers
  with OptionValues
  with DefaultAwaitTimeout
  with FutureAwaits
  with BeforeAndAfterAll
  with BeforeAndAfterEach {

  private val currentSession = "session 1"
  private val anotherSession = "session 2"

  private val flowRepository = app.injector.instanceOf[FlowRepository]

  override protected def beforeEach(): Unit = {
    await(flowRepository.collection.drop.toFuture())
    await(flowRepository.ensureIndexes)
  }

  override protected def afterAll(): Unit = {
   await(flowRepository.collection.drop.toFuture())
  }

  trait PopulatedSetup {
    val currentFlow: IpAllowlistFlow = IpAllowlistFlow(currentSession, Set("ip1", "ip2"))
    val flowInDifferentSession: IpAllowlistFlow = IpAllowlistFlow(anotherSession, Set("ip3", "ip4"))
    val flowOfDifferentType: EmailPreferencesFlowV2 = EmailPreferencesFlowV2(currentSession,
      selectedCategories = Set("category1", "category2"),
      selectedAPIs = Map("category1" -> Set("qwqw", "asass")),
      selectedTopics = Set("BUSINESS_AND_POLICY"),
      visibleApis = List(CombinedApi("api1ServiceName", "api1Name",
                    List(CombinedApiCategory("VAT"), CombinedApiCategory("AGENT")), REST_API)))

    await(flowRepository.saveFlow(currentFlow))
    await(flowRepository.saveFlow(flowInDifferentSession))
    await(flowRepository.saveFlow(flowOfDifferentType))
    await(flowRepository.collection.countDocuments().toFuture()) shouldBe 3

    def fetchLastUpdated(flow: Flow): LocalDateTime = {
      val query = Document("sessionId" -> Codecs.toBson(flow.sessionId), "flowType" -> Codecs.toBson(flow.flowType))

      await(flowRepository.collection.aggregate[BsonValue](
        Seq(
        filter(query),
        project(fields(Projections.excludeId(), Projections.include("lastUpdated"))))
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

        val result= await(flowRepository.collection.find(Filters.equal("sessionId", currentSession)).headOption())
        result match {
          case Some(savedFlow: IpAllowlistFlow ) =>
            savedFlow.sessionId shouldBe currentSession
            savedFlow.flowType shouldBe IP_ALLOW_LIST
            savedFlow.allowlist shouldBe Set("ip1", "ip2")
          case _ => fail
        }

      }

      "save email preferences" in {
        val flow =  EmailPreferencesFlowV2(currentSession,
          selectedCategories= Set("category1", "category2"),
          selectedAPIs = Map("category1" -> Set("qwqw", "asass")),
          selectedTopics = Set("BUSINESS_AND_POLICY",  "EVENT_INVITES"),
          visibleApis = List(CombinedApi("api1ServiceName", "api1DisplayName",  List(CombinedApiCategory("VAT"), CombinedApiCategory("AGENT")), REST_API)))

        await(flowRepository.saveFlow(flow))

        val result: Flow = await(flowRepository.collection.find(Filters.equal("sessionId", Codecs.toBson(currentSession))).first.toFuture())
        val castResult =  result.asInstanceOf[EmailPreferencesFlowV2]
        castResult.sessionId shouldBe currentSession
        castResult.flowType shouldBe EMAIL_PREFERENCES_V2
        castResult.selectedTopics shouldBe Set(EmailTopic.BUSINESS_AND_POLICY.toString, EmailTopic.EVENT_INVITES.toString)
        castResult.visibleApis should contain only (CombinedApi("api1ServiceName", "api1DisplayName",   List(CombinedApiCategory("VAT"), CombinedApiCategory("AGENT")), REST_API))
      }

      "update the flow when it already exists" in new PopulatedSetup {
        await(flowRepository.saveFlow(currentFlow))
        val updatedFlow: IpAllowlistFlow = currentFlow.copy(allowlist = Set("new IP"))

        val result: IpAllowlistFlow = await(flowRepository.saveFlow(updatedFlow))

        result shouldBe updatedFlow
        val updatedDocument: IpAllowlistFlow = await(flowRepository.collection
          .find(Document("sessionId" -> currentSession, "flowType" ->  FlowType.IP_ALLOW_LIST.toString)).map(_.asInstanceOf[IpAllowlistFlow]).head())

        updatedDocument.allowlist shouldBe Set("new IP")
      }
    }

    "deleteBySessionId" should {
      "delete only the flow for the specified session ID and flow type" in new PopulatedSetup {
        val result: Boolean = await(flowRepository.deleteBySessionIdAndFlowType(currentSession, FlowType.IP_ALLOW_LIST))

        result shouldBe true
        await(flowRepository.collection.countDocuments().toFuture()) shouldBe 2
        await(flowRepository.fetchBySessionIdAndFlowType(currentSession, FlowType.IP_ALLOW_LIST)(formatIpAllowlistFlow)) shouldBe None
      }

      "return false if it did not have anything to delete" in {
        val result: Boolean = await(flowRepository.deleteBySessionIdAndFlowType("session 1", FlowType.IP_ALLOW_LIST))

        result shouldBe true
      }
    }

    "fetchBySessionIdAndFlowType" should {

      "fetch the flow for the specified session ID and flow type" in new PopulatedSetup {

        val result = await(flowRepository.fetchBySessionIdAndFlowType(currentSession, FlowType.IP_ALLOW_LIST)(formatIpAllowlistFlow))

        result shouldBe Some(currentFlow)
      }

      "return None when the query does not match any data" in {
        val result = await(flowRepository.fetchBySessionIdAndFlowType("session 1", FlowType.IP_ALLOW_LIST)(formatIpAllowlistFlow))

        result shouldBe None
      }
    }

    "updateLastUpdated" should {
      "update lastUpdated for all flows for the specified session ID" in new PopulatedSetup {
        val lastUpdatedInCurrentFlow: LocalDateTime = fetchLastUpdated(currentFlow)
        val lastUpdatedInFlowOfDifferentType: LocalDateTime = fetchLastUpdated(flowOfDifferentType)
        val lastUpdatedInFlowInDifferentSession: LocalDateTime = fetchLastUpdated(flowInDifferentSession)

        await(flowRepository.updateLastUpdated(currentSession))

        fetchLastUpdated(currentFlow).isAfter(lastUpdatedInCurrentFlow) shouldBe true
        fetchLastUpdated(flowOfDifferentType).isAfter(lastUpdatedInFlowOfDifferentType) shouldBe true
        fetchLastUpdated(flowInDifferentSession) shouldBe lastUpdatedInFlowInDifferentSession
      }
    }
  }
}

case class ResultSet(lastUpdated: LocalDateTime)

object ResultSet   {
  import play.api.libs.json.Json
  implicit val dateFormat: Format[LocalDateTime] = MongoJavatimeFormats.localDateTimeFormat
  implicit val resultSetFormat: OFormat[ResultSet] = Json.format[ResultSet]

  def apply(lastUpdated: LocalDateTime) = new ResultSet(lastUpdated)
}
