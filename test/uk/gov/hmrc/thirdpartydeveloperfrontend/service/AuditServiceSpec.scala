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

package uk.gov.hmrc.thirdpartydeveloperfrontend.service

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global

import org.mockito.ArgumentMatcher

import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.DataEvent

import uk.gov.hmrc.apiplatform.modules.common.utils.FixedClock
import uk.gov.hmrc.apiplatform.modules.tpd.session.domain.models.UserSession
import uk.gov.hmrc.apiplatform.modules.tpd.test.data.UserTestData
import uk.gov.hmrc.apiplatform.modules.tpd.test.utils.LocalUserIdTracker
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.DeveloperSessionBuilder
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ApplicationConfig
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.AuditAction.{ApplicationUpliftRequestDeniedDueToInvalidCredentials, PasswordChangeFailedDueToInvalidCredentials}
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.AsyncHmrcSpec

class AuditServiceSpec extends AsyncHmrcSpec {

  trait Setup extends LocalUserIdTracker with DeveloperSessionBuilder with UserTestData with FixedClock {

    val developerSession: UserSession = standardDeveloper.loggedIn

    implicit val hc: HeaderCarrier = HeaderCarrier().withExtraHeaders(
      "X-email-address" -> developerSession.developer.email.text,
      "X-name"          -> developerSession.developer.displayedName
    )

    val mockAuditConnector = mock[AuditConnector]
    val mockAppConfig      = mock[ApplicationConfig]
    val underTest          = new AuditService(mockAuditConnector, mockAppConfig)

    def verifyPasswordChangeFailedAuditEventSent(tags: Map[String, String])(implicit hc: HeaderCarrier) = {

      val expectedEvent = new DataEvent(
        auditSource = "third-party-developer-frontend",
        auditType = "PasswordChangeFailedDueToInvalidCredentials",
        tags = Map(
          "transactionName" -> "Password change request has been denied, due to invalid credentials"
        ) ++ tags,
        detail = Map()
      )

      underTest.audit(PasswordChangeFailedDueToInvalidCredentials(developerSession.developer.email))

      verify(mockAuditConnector).sendEvent(argThat(isSameDataEvent(expectedEvent)))(*, any[ExecutionContext])
    }
  }

  "Audit Service" should {

    "send an event when the application uplift fails due to invalid credentials" in new Setup {

      val expectedEvent = new DataEvent(
        auditSource = "third-party-developer-frontend",
        auditType = "ApplicationUpliftRequestDeniedDueToInvalidCredentials",
        tags = Map(
          "transactionName"   -> "Application uplift to production request has been denied, due to invalid credentials",
          "developerFullName" -> developerSession.developer.displayedName,
          "developerEmail"    -> developerSession.developer.email.text
        ),
        detail = Map(
          "applicationId" -> "123456"
        )
      )

      val event = ApplicationUpliftRequestDeniedDueToInvalidCredentials("123456")

      underTest.audit(event)

      verify(mockAuditConnector).sendEvent(argThat(isSameDataEvent(expectedEvent)))(*, any[ExecutionContext])
    }

    "send an event when the password change fails due to invalid credentials for a user who is logged in" in new Setup {

      verifyPasswordChangeFailedAuditEventSent(tags = Map("developerEmail" -> developerSession.developer.email.text, "developerFullName" -> developerSession.developer.displayedName))
    }

    "send an event when the password change fails due to invalid credentials for a user who is not logged in" in new Setup {

      implicit override val hc: HeaderCarrier = HeaderCarrier()

      verifyPasswordChangeFailedAuditEventSent(tags = Map("developerEmail" -> developerSession.developer.email.text))
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
