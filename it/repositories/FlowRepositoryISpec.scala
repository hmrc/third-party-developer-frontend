package repositories

import akka.stream.Materializer
import config.ApplicationConfig
import domain.models.apidefinitions.ApiContext
import domain.models.connectors.ApiDefinition
import domain.models.flows.{EmailPreferencesFlow, Flow, FlowType, IpAllowlistFlow}
import model.EmailTopic
import org.joda.time.DateTime
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.libs.json.{JsObject, Json}
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.play.json.ImplicitBSONHandlers.JsObjectDocumentWriter
import repositories.ReactiveMongoFormatters._
import uk.gov.hmrc.mongo.{MongoConnector, MongoSpecSupport}

import scala.concurrent.ExecutionContext.Implicits.global
import domain.models.flows.FlowType._
import reactivemongo.bson.BSONLong

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
    val flowOfDifferentType: EmailPreferencesFlow = EmailPreferencesFlow(currentSession,
      selectedCategories = Set("category1", "category2"),
      selectedAPIs = Map("category1" -> Set("qwqw", "asass")),
      selectedTopics = Set("BUSINESS_AND_POLICY"),
      visibleApis = Seq(ApiDefinition("api1ServiceName", "api1Name", "api1Desc", ApiContext("api1Context"), Seq("VAT", "AGENT"))))
    await(repository.saveFlow(currentFlow))
    await(repository.saveFlow(flowInDifferentSession))
    await(repository.saveFlow(flowOfDifferentType))
    await(repository.findAll()) should have size 3

    def fetchLastUpdated(flow: Flow): DateTime = {
      (await(repository.collection
        .find[JsObject, JsObject](Json.obj("sessionId" -> flow.sessionId, "flowType" -> flow.flowType))
        .one[JsObject]).get \ "lastUpdated")
        .as[DateTime]
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
        (result \ "lastUpdated").asOpt[DateTime] should not be empty
        (result \ "allowlist").as[Set[String]] should contain only ("ip1", "ip2")
      }

      "save email preferences" in {
        val flow =  EmailPreferencesFlow(currentSession,
          selectedCategories= Set("category1", "category2"),
          selectedAPIs = Map("category1" -> Set("qwqw", "asass")),
          selectedTopics = Set("BUSINESS_AND_POLICY",  "EVENT_INVITES"),
        visibleApis = Seq(ApiDefinition("api1ServiceName", "api1Name", "api1Desc", ApiContext("api1Context"), Seq("VAT", "AGENT"))))

        await(repository.saveFlow(flow))

        val Some(result) = await(repository.collection.find[JsObject, JsObject](Json.obj("sessionId" -> currentSession)).one[JsObject])
        (result \ "sessionId").as[String] shouldBe currentSession
        (result \ "flowType").as[String] shouldBe EMAIL_PREFERENCES.toString()
        (result \ "lastUpdated").asOpt[DateTime] should not be empty
        (result \ "selectedTopics").as[Set[EmailTopic]] should contain only (EmailTopic.BUSINESS_AND_POLICY, EmailTopic.EVENT_INVITES)
        (result \ "visibleApis").as[Seq[ApiDefinition]] should contain only (ApiDefinition("api1ServiceName", "api1Name", "api1Desc", ApiContext("api1Context"), Seq("VAT", "AGENT")))
      }

      "update the flow when it already exists" in new PopulatedSetup {
        val lastUpdatedInCurrentFlow: DateTime = fetchLastUpdated(currentFlow)
        val updatedFlow: IpAllowlistFlow = currentFlow.copy(allowlist = Set("new IP"))

        val result: IpAllowlistFlow = await(repository.saveFlow(updatedFlow))

        result shouldBe updatedFlow
        val Some(updatedDocument) = await(repository.collection
          .find[JsObject, JsObject](Json.obj("sessionId" -> currentSession, "flowType" -> FlowType.IP_ALLOW_LIST.toString())).one[JsObject])
        (updatedDocument \ "lastUpdated").as[DateTime].isAfter(lastUpdatedInCurrentFlow) shouldBe true
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
        val lastUpdatedInCurrentFlow: DateTime = fetchLastUpdated(currentFlow)
        val lastUpdatedInFlowOfDifferentType: DateTime = fetchLastUpdated(flowOfDifferentType)
        val lastUpdatedInFlowInDifferentSession: DateTime = fetchLastUpdated(flowInDifferentSession)

        await(repository.updateLastUpdated(currentSession))

        fetchLastUpdated(currentFlow).isAfter(lastUpdatedInCurrentFlow) shouldBe true
        fetchLastUpdated(flowOfDifferentType).isAfter(lastUpdatedInFlowOfDifferentType) shouldBe true
        fetchLastUpdated(flowInDifferentSession) shouldBe lastUpdatedInFlowInDifferentSession
      }
    }
  }
}
