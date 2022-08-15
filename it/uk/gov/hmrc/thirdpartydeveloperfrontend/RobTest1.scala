package uk.gov.hmrc.thirdpartydeveloperfrontend

import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.http.Status.OK
import play.api.libs.ws.WSClient
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}


class RobTest1 extends PlaySpec with GuiceOneServerPerSuite with FutureAwaits with DefaultAwaitTimeout{

  "test login" in {
    val wsClient              = app.injector.instanceOf[WSClient]
    val response = await(wsClient.url("http://localhost:9685/developer/login").get())

    response.status mustBe OK
  }

  "test server logic" in {
    val wsClient              = app.injector.instanceOf[WSClient]
    val response = await(wsClient.url("http://localhost:9685/developer/applications").get())

    println(response.body)
    response.status mustBe OK
  }
}
