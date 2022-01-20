/*
 * Copyright 2022 HM Revenue & Customs
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

import java.time.Period

import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ApplicationConfig
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._
import org.joda.time.{DateTime, Duration, Instant, LocalDate}
import uk.gov.hmrc.http.HeaderCarrier
import mocks.service._

import scala.concurrent.ExecutionContext.Implicits.global
import utils._

class MfaMandateServiceSpec extends AsyncHmrcSpec with CollaboratorTracker with LocalUserIdTracker with AppsByTeamMemberServiceMock {

  trait Setup {
    val dateAFewDaysAgo: LocalDate = Instant.now().minus(Duration.standardDays((2L))).toDateTime().toLocalDate
    val dateToday: LocalDate = Instant.now().toDateTime().toLocalDate
    val dateInTheFuture: LocalDate = Instant.now().plus(Duration.standardDays(1L)).toDateTime().toLocalDate

    val email = "test@example.com"
    val userId = idOf(email)

    implicit val mockHeaderCarrier = mock[HeaderCarrier]

    val mockAppConfig: ApplicationConfig = mock[ApplicationConfig]

    val service = new MfaMandateService(mockAppConfig, appsByTeamMemberServiceMock)

    val applicationId = ApplicationId("myId")
    val clientId = ClientId("myClientId")
    val grantLength: Period = Period.ofDays(547)

    val applicationsWhereUserIsAdminInProduction = 
      Seq(
        ApplicationWithSubscriptionIds(
          applicationId,
          clientId,
          "myName",
          new DateTime(),
          new DateTime(),
          None,
          grantLength,
          Environment.PRODUCTION,
          collaborators = Set(email.asAdministratorCollaborator),
          subscriptions = Set.empty
        )
      )

    val applicationsWhereUserIsDeveloperInProduction = 
      Seq(
        ApplicationWithSubscriptionIds(
          applicationId,
          clientId,
          "myName",
          new DateTime(),
          new DateTime(),
          None,
          grantLength,
          Environment.PRODUCTION,
          collaborators = Set(email.asDeveloperCollaborator),
          subscriptions = Set.empty
        )
      )

    val applicationsWhereUserIsNotACollaboratorInProduction =
      Seq(
        ApplicationWithSubscriptionIds(
          applicationId,
          clientId,
          "myName",
          new DateTime(),
          new DateTime(),
          None,
          grantLength,
          Environment.PRODUCTION,
          subscriptions = Set.empty          
        )
      )
  }

  "showAdminMfaMandateMessage" when {
    "Mfa mandate date has passed" should {
      "be false" in new Setup {
        when(mockAppConfig.dateOfAdminMfaMandate).thenReturn(Some(dateAFewDaysAgo))

        fetchByTeamMembersWithRoleReturns(applicationsWhereUserIsAdminInProduction)

        await(service.showAdminMfaMandatedMessage(userId)) shouldBe false
      }

      "Mfa mandate date was today" should {
        "be false" in new Setup {
          when(mockAppConfig.dateOfAdminMfaMandate).thenReturn(Some(dateToday))
          fetchByTeamMembersWithRoleReturns(applicationsWhereUserIsAdminInProduction)

          await(service.showAdminMfaMandatedMessage(userId)) shouldBe false
        }
      }
    }

    "Mfa mandate date has not passed and they are an admin on a principal application" should {
      "be true" in new Setup {
        when(mockAppConfig.dateOfAdminMfaMandate).thenReturn(Some(dateInTheFuture))
        fetchByTeamMembersWithRoleReturns(applicationsWhereUserIsAdminInProduction)

        await(service.showAdminMfaMandatedMessage(userId)) shouldBe true
      }
    }

    "Mfa mandate date has not passed and they are not an admin on a principle application" should {
      "be false" in new Setup {
        when(mockAppConfig.dateOfAdminMfaMandate).thenReturn(Some(dateInTheFuture))
        fetchByTeamMembersWithRoleReturns(Nil)

        await(service.showAdminMfaMandatedMessage(userId)) shouldBe false
      }
    }

    "Mfa mandate date has not passed and they are a developer on a principle application" should {
      "be false" in new Setup {
        when(mockAppConfig.dateOfAdminMfaMandate).thenReturn(Some(dateInTheFuture))
        fetchByTeamMembersWithRoleReturns(Nil)

        await(service.showAdminMfaMandatedMessage(userId)) shouldBe false
      }
    }

    "Mfa mandate date has not passed and they are are not a collaborator on a principle application" should {
      "be false" in new Setup {
        when(mockAppConfig.dateOfAdminMfaMandate).thenReturn(Some(dateInTheFuture))
        fetchByTeamMembersWithRoleReturns(Nil)
        
        await(service.showAdminMfaMandatedMessage(userId)) shouldBe false
      }
    }

    "Mfa mandate date is not set" should {
      "be false" in new Setup {
        when(mockAppConfig.dateOfAdminMfaMandate).thenReturn(None)
        fetchByTeamMembersWithRoleReturns(applicationsWhereUserIsAdminInProduction)

        await(service.showAdminMfaMandatedMessage(userId)) shouldBe false
      }
    }
  }

  "isMfaMandatedForUser" when {
    "Mfa mandate date has passed" should {
      "be true" in new Setup {
        when(mockAppConfig.dateOfAdminMfaMandate).thenReturn(Some(dateAFewDaysAgo))
        fetchByTeamMembersWithRoleReturns(applicationsWhereUserIsAdminInProduction)

        await(service.isMfaMandatedForUser(userId)) shouldBe true
      }
    }

    "Mfa mandate date was today" should {
      "be true" in new Setup {
        when(mockAppConfig.dateOfAdminMfaMandate).thenReturn(Some(dateToday))
        fetchByTeamMembersWithRoleReturns(applicationsWhereUserIsAdminInProduction)

        await(service.isMfaMandatedForUser(userId)) shouldBe true
      }
    }

    "Mfa mandate date has not passed and they are an admin on a principal application" should {
      "be false" in new Setup {
        when(mockAppConfig.dateOfAdminMfaMandate).thenReturn(Some(dateInTheFuture))
        fetchByTeamMembersWithRoleReturns(applicationsWhereUserIsAdminInProduction)

        await(service.isMfaMandatedForUser(userId)) shouldBe false
      }
    }

    "Mfa mandate date has passed and they are not an admin on a principle application" should {
      "be false" in new Setup {
        when(mockAppConfig.dateOfAdminMfaMandate).thenReturn(Some(dateAFewDaysAgo))
        fetchByTeamMembersWithRoleReturns(Nil)

        await(service.isMfaMandatedForUser(userId)) shouldBe false
      }
    }

    "Mfa mandate date has passed and they are a developer on a principle application" should {
      "be false" in new Setup {
        when(mockAppConfig.dateOfAdminMfaMandate).thenReturn(Some(dateAFewDaysAgo))

        fetchByTeamMembersWithRoleReturns(Nil)

        await(service.isMfaMandatedForUser(userId)) shouldBe false
      }
    }

    "Mfa mandate date has passed and they are are not a collaborator on a principle application" should {
      "be false" in new Setup {
        when(mockAppConfig.dateOfAdminMfaMandate).thenReturn(Some(dateAFewDaysAgo))
        
        fetchByTeamMembersWithRoleReturns(Nil)

        await(service.isMfaMandatedForUser(userId)) shouldBe false
      }
    }

    "Mfa mandate date is not set" should {
      "be false" in new Setup {
        when(mockAppConfig.dateOfAdminMfaMandate).thenReturn(None)
        fetchByTeamMembersWithRoleReturns(applicationsWhereUserIsAdminInProduction)

        await(service.isMfaMandatedForUser(userId)) shouldBe false
      }
    }
  }

  "daysTillAdminMfaMandate" when {
    "mfaAdminMandateDate is 1 day in the future" should {
      "be 1" in new Setup {
        when(mockAppConfig.dateOfAdminMfaMandate).thenReturn(Some(dateInTheFuture))

        service.daysTillAdminMfaMandate shouldBe Some(1)
      }
    }

    "mfaAdminMandateDate is now" should {
      "be 0" in new Setup {
        when(mockAppConfig.dateOfAdminMfaMandate).thenReturn(Some(dateToday))

        service.daysTillAdminMfaMandate shouldBe Some(0)
      }
    }

    "mfaAdminMandateDate is in the past" should {
      "be none" in new Setup {
        when(mockAppConfig.dateOfAdminMfaMandate).thenReturn(Some(dateAFewDaysAgo))

        service.daysTillAdminMfaMandate shouldBe None
      }
    }
  }

  "daysTillAdminMfaMandate" when {
    "mfaAdminMandateDate is not set" should {
      "be none" in new Setup {
        when(mockAppConfig.dateOfAdminMfaMandate).thenReturn(None)

        service.daysTillAdminMfaMandate shouldBe None
      }
    }
  }

  "parseLocalDate" when {
    "an empty date value is used" should {
      "parse to None" in {
        MfaMandateService.parseLocalDate("") shouldBe None
      }
    }

    "an whitespace date value is used" should {
      "parse to None" in {
        MfaMandateService.parseLocalDate(" ") shouldBe None
      }
    }

    "the date 2001-02-03 is used" should {
      "parse to a 2001-02-03" in {
        val year = 2001
        val month = 2
        val day = 3
        MfaMandateService.parseLocalDate("2001-02-03") shouldBe Some(new LocalDate(year, month, day))
      }
    }
  }
}
