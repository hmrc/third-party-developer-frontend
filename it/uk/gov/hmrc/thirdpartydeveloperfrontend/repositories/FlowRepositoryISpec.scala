package uk.gov.hmrc.thirdpartydeveloperfrontend.repositories

import akka.stream.Materializer
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ApplicationConfig
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors.ApiType.REST_API
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors.{CombinedApi, CombinedApiCategory}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.emailpreferences.EmailTopic
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.flows.FlowType._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.flows.{EmailPreferencesFlowV2, Flow, FlowType, IpAllowlistFlow}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.libs.json.{JsObject, Json}
import play.modules.reactivemongo.ReactiveMongoComponent
import uk.gov.hmrc.thirdpartydeveloperfrontend.repositories.MongoFormatters._
import uk.gov.hmrc.mongo.{MongoConnector, MongoSpecSupport}

import java.time.LocalDateTime
import scala.concurrent.ExecutionContext.Implicits.global

class FlowRepositoryISpec extends BaseRepositoryIntegrationSpec with MongoSpecSupport with GuiceOneAppPerSuite {

  implicit lazy val materializer: Materializer = app.materializer
  private val currentSession = "session 1"
  private val anotherSession = "session 2"

  private val reactiveMongoComponent: ReactiveMongoComponent = new ReactiveMongoComponent {
    override val mongoConnector: MongoConnector = mongoConnectorForTest
  }

  private val appConfig = app.injector.instanceOf[ApplicationConfig]
  private val repository = new FlowRepository(reactiveMongoComponent, appConfig)

  override protected def beforeEach() {
    await(repository.drop)
    await(repository.ensureIndexes)
  }

  override protected def afterAll() {
   await(repository.drop)
  }

  trait PopulatedSetup {

    val currentFlow: IpAllowlistFlow = IpAllowlistFlow(currentSession, Set("ip1", "ip2"))
    val flowInDifferentSession: IpAllowlistFlow = IpAllowlistFlow(anotherSession, Set("ip3", "ip4"))
    val flowOfDifferentType: EmailPreferencesFlowV2 = EmailPreferencesFlowV2(currentSession,
      selectedCategories = Set("category1", "category2"),
      selectedAPIs = Map("category1" -> Set("qwqw", "asass")),
      selectedTopics = Set("BUSINESS_AND_POLICY"),
      visibleApis = List(CombinedApi("api1ServiceName", "api1Name",  List(CombinedApiCategory("VAT"), CombinedApiCategory("AGENT")), REST_API)))
    await(repository.saveFlow(currentFlow))
    await(repository.saveFlow(flowInDifferentSession))
    await(repository.saveFlow(flowOfDifferentType))
    await(repository.findAll()) should have size 3

    def fetchLastUpdated(flow: Flow): LocalDateTime = {
      (await(repository.collection
        .find[JsObject, JsObject](Json.obj("sessionId" -> flow.sessionId, "flowType" -> flow.flowType))
        .one[JsObject]).get \ "lastUpdated")
        .as[LocalDateTime]
    }
  }

  "FlowRepository" when {
    "Indexes" should {
      "create ttl index and it should have correct value matching the session timeout in seconds "in {
        val mayBeIndex = await(repository.collection.indexesManager.list().map(_.find(_.eventualName.equalsIgnoreCase("last_updated_ttl_idx"))))
        mayBeIndex shouldNot be(None)
        val mayBeTtlValue: Option[Long] = mayBeIndex.flatMap(_.options.getAs[BSONLong]("expireAfterSeconds").map(_.as[Long]))
        mayBeTtlValue shouldNot be(None)
        mayBeTtlValue.head shouldBe appConfig.sessionTimeoutInSeconds
      }
    }

    "saveFlow" should {
      "save IP allowlist" in {
        val flow = IpAllowlistFlow(currentSession, Set("ip1", "ip2"))

        await(repository.saveFlow(flow))

        val Some(result) = await(repository.collection.find[JsObject, JsObject](Json.obj("sessionId" -> currentSession)).one[JsObject])
        (result \ "sessionId").as[String] shouldBe currentSession
        (result \ "flowType").as[String] shouldBe IP_ALLOW_LIST.toString()
        (result \ "lastUpdated").asOpt[LocalDateTime] should not be empty
        (result \ "allowlist").as[Set[String]] should contain only ("ip1", "ip2")
      }

      "save email preferences" in {
        val flow =  EmailPreferencesFlowV2(currentSession,
          selectedCategories= Set("category1", "category2"),
          selectedAPIs = Map("category1" -> Set("qwqw", "asass")),
          selectedTopics = Set("BUSINESS_AND_POLICY",  "EVENT_INVITES"),
        visibleApis = List(CombinedApi("api1ServiceName", "api1DisplayName",  List(CombinedApiCategory("VAT"), CombinedApiCategory("AGENT")), REST_API)))

        await(repository.saveFlow(flow))

        val Some(result) = await(repository.collection.find[JsObject, JsObject](Json.obj("sessionId" -> currentSession)).one[JsObject])
        (result \ "sessionId").as[String] shouldBe currentSession
        (result \ "flowType").as[String] shouldBe EMAIL_PREFERENCES_V2.toString
        (result \ "lastUpdated").asOpt[LocalDateTime] should not be empty
        (result \ "selectedTopics").as[Set[EmailTopic]] should contain only (EmailTopic.BUSINESS_AND_POLICY, EmailTopic.EVENT_INVITES)
        (result \ "visibleApis").as[List[CombinedApi]] should contain only (CombinedApi("api1ServiceName", "api1DisplayName",   List(CombinedApiCategory("VAT"), CombinedApiCategory("AGENT")), REST_API))
      }

      "update the flow when it already exists" in new PopulatedSetup {
        val lastUpdatedInCurrentFlow: LocalDateTime = fetchLastUpdated(currentFlow)
        val updatedFlow: IpAllowlistFlow = currentFlow.copy(allowlist = Set("new IP"))

        val result: IpAllowlistFlow = await(repository.saveFlow(updatedFlow))

        result shouldBe updatedFlow
        val Some(updatedDocument) = await(repository.collection
          .find[JsObject, JsObject](Json.obj("sessionId" -> currentSession, "flowType" -> FlowType.IP_ALLOW_LIST.toString())).one[JsObject])
        (updatedDocument \ "lastUpdated").as[LocalDateTime].isAfter(lastUpdatedInCurrentFlow) shouldBe true
        (updatedDocument \ "allowlist").as[Set[String]] should contain only "new IP"
      }
    }

    "deleteBySessionId" should {
      "delete only the flow for the specified session ID and flow type" in new PopulatedSetup {
        val result: Boolean = await(repository.deleteBySessionIdAndFlowType(currentSession, FlowType.IP_ALLOW_LIST))

        result shouldBe true
        await(repository.findAll()) should have size 2
        await(repository.fetchBySessionIdAndFlowType(currentSession, FlowType.IP_ALLOW_LIST)(formatIpAllowlistFlow)) shouldBe None
      }

      "return false if it did not have anything to delete" in {
        val result: Boolean = await(repository.deleteBySessionIdAndFlowType("session 1", FlowType.IP_ALLOW_LIST))

        result shouldBe true
      }
    }

    "fetchBySessionIdAndFlowType" should {

      "fetch the flow for the specified session ID and flow type" in new PopulatedSetup {

        val result: Option[IpAllowlistFlow] = await(repository.fetchBySessionIdAndFlowType(currentSession, FlowType.IP_ALLOW_LIST)(formatIpAllowlistFlow))

        result shouldBe Some(currentFlow)
      }

      "return None when the query does not match any data" in {
        val result: Option[IpAllowlistFlow] = await(repository.fetchBySessionIdAndFlowType("session 1", FlowType.IP_ALLOW_LIST)(formatIpAllowlistFlow))

        result shouldBe None
      }
    }

    "updateLastUpdated" should {
      "update lastUpdated for all flows for the specified session ID" in new PopulatedSetup {
        val lastUpdatedInCurrentFlow: LocalDateTime = fetchLastUpdated(currentFlow)
        val lastUpdatedInFlowOfDifferentType: LocalDateTime = fetchLastUpdated(flowOfDifferentType)
        val lastUpdatedInFlowInDifferentSession: LocalDateTime = fetchLastUpdated(flowInDifferentSession)

        await(repository.updateLastUpdated(currentSession))

        fetchLastUpdated(currentFlow).isAfter(lastUpdatedInCurrentFlow) shouldBe true
        fetchLastUpdated(flowOfDifferentType).isAfter(lastUpdatedInFlowOfDifferentType) shouldBe true
        fetchLastUpdated(flowInDifferentSession) shouldBe lastUpdatedInFlowInDifferentSession
      }
    }
  }
}
