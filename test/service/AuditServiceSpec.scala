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

package service

import config.ApplicationConfig
import domain.LoggedInState
import org.mockito.ArgumentMatcher
import org.mockito.ArgumentMatchers.{any, argThat}
import org.mockito.Mockito._
import org.scalatest.Matchers
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import service.AuditAction.{ApplicationUpliftRequestDeniedDueToInvalidCredentials, PasswordChangeFailedDueToInvalidCredentials}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.DataEvent
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global

class AuditServiceSpec extends UnitSpec with Matchers with MockitoSugar with ScalaFutures {

  val developer = utils.DeveloperSession("email@example.com", "Paul", "Smith", loggedInState = LoggedInState.LOGGED_IN)

  trait Setup {
    implicit val hc = HeaderCarrier().withExtraHeaders(
      "X-email-address" -> developer.email,
      "X-name" -> developer.displayedName
    )

    val mockAuditConnector = mock[AuditConnector]
    val mockAppConfig = mock[ApplicationConfig]
    val underTest = new AuditService(mockAuditConnector, mockAppConfig)

    def verifyPasswordChangeFailedAuditEventSent(tags: Map[String, String])(implicit hc: HeaderCarrier) = {

      val expectedEvent = new DataEvent(
        auditSource = "third-party-developer-frontend",
        auditType = "PasswordChangeFailedDueToInvalidCredentials",
        tags = Map(
          "transactionName" -> "Password change request has been denied, due to invalid credentials"
        ) ++ tags,
        detail = Map()
      )

      underTest.audit(PasswordChangeFailedDueToInvalidCredentials(developer.email))

      verify(mockAuditConnector).sendEvent(argThat(isSameDataEvent(expectedEvent)))(any[HeaderCarrier], any[ExecutionContext])
    }
  }

  "Audit Service" should {

    "send an event when the application uplift fails due to invalid credentials" in new Setup {

      val expectedEvent = new DataEvent(
        auditSource = "third-party-developer-frontend",
        auditType = "ApplicationUpliftRequestDeniedDueToInvalidCredentials",
        tags = Map(
          "transactionName" -> "Application uplift to production request has been denied, due to invalid credentials",
          "developerFullName" -> developer.displayedName,
          "developerEmail" -> developer.email
        ),
        detail = Map(
          "applicationId" -> "123456"
        )
      )

      val event = ApplicationUpliftRequestDeniedDueToInvalidCredentials("123456")

      underTest.audit(event)

      verify(mockAuditConnector).sendEvent(argThat(isSameDataEvent(expectedEvent)))(any[HeaderCarrier], any[ExecutionContext])
    }

    "send an event when the password change fails due to invalid credentials for a user who is logged in" in new Setup {

      verifyPasswordChangeFailedAuditEventSent(tags = Map("developerEmail" -> developer.email, "developerFullName" -> developer.displayedName))
    }

    "send an event when the password change fails due to invalid credentials for a user who is not logged in" in new Setup {

      override implicit val hc = HeaderCarrier()

      verifyPasswordChangeFailedAuditEventSent(tags = Map("developerEmail" -> developer.email))
    }
  }

  private def isSameDataEvent(expected: DataEvent) =
    new ArgumentMatcher[DataEvent] {
      override def matches(actual: DataEvent) = actual match {
        case de: DataEvent =>
          de.auditSource == expected.auditSource &&
            de.auditType == expected.auditType &&
            expected.tags.toSet.subsetOf(de.tags.toSet) &&
            expected.detail.toSet.subsetOf(de.detail.toSet)
      }
    }
}
