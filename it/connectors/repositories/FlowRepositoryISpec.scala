package repositories

import uk.gov.hmrc.mongo.MongoSpecSupport


import play.modules.reactivemongo.ReactiveMongoComponent
import uk.gov.hmrc.mongo.{MongoConnector, MongoSpecSupport}
import scala.concurrent.duration._
import org.scalatestplus.play.guice.GuiceOneAppPerSuite

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import domain.models.flows.{IpAllowlistFlow, EmailPreferencesFlow}
import play.api.libs.json.{JsObject, Json, OWrites, Reads}
import reactivemongo.play.json.ImplicitBSONHandlers.JsObjectDocumentWriter
import model.EmailTopic

class FlowRepositoryISpec extends BaseRepositoryIntegrationSpec with MongoSpecSupport with GuiceOneAppPerSuite{

  implicit lazy val materializer = app.materializer
  private val expiry: Duration = 15 minutes


  private val reactiveMongoComponent: ReactiveMongoComponent = new ReactiveMongoComponent {
    override val mongoConnector: MongoConnector = mongoConnectorForTest
  }

  private val repository = new FlowRepository(reactiveMongoComponent)

  override protected def beforeEach() {
    await(repository.drop)
    await(repository.ensureIndexes)
  }

  override protected def afterAll() {
    await(repository.drop)
  }

  "FlowRepository" when {
    "save" should {
      "do summat" in {
        val sessionId = "123456"
        implicit val reads = ReactiveMongoFormatters.formatIpAllowlistFlow
        val flow = IpAllowlistFlow(sessionId, Set("ip1", "ip2"))
        await(repository.saveFlow(flow))
        val result = await(repository.collection.find[JsObject, JsObject](Json.obj("sessionId" -> sessionId)).one[JsObject])
        println(result.get.toString())
        (result.get \ "flowType").as[String] shouldBe IpAllowlistFlow.toString()
      }

    "do summat else" in {
        val sessionId = "123456"
        implicit val reads = ReactiveMongoFormatters.formatEmailPreferencesFlow
        val flow = EmailPreferencesFlow(sessionId, Set(EmailTopic.BUSINESS_AND_POLICY, EmailTopic.EVENT_INVITES))
        await(repository.saveFlow(flow))
        val result = await(repository.collection.find[JsObject, JsObject](Json.obj("sessionId" -> sessionId)).one[JsObject])
        println(result.get.toString())
        (result.get \ "flowType").as[String] shouldBe EmailPreferencesFlow.toString()
      }
    }
    
    "fetch" should {

    }
  }

}
