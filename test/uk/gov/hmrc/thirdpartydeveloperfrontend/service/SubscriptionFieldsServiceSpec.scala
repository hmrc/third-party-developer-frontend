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

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.{ApplicationWithCollaboratorsFixtures, Collaborator}
import uk.gov.hmrc.apiplatform.modules.applications.subscriptions.domain.models.FieldValue
import uk.gov.hmrc.apiplatform.modules.common.domain.models._
import uk.gov.hmrc.apiplatform.modules.common.utils.FixedClock
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.SubscriptionsBuilder
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.{ApmConnector, ThirdPartyApplicationConnector}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.subscriptions.ApiSubscriptionFields.{
  SaveSubscriptionFieldsAccessDeniedResponse,
  SaveSubscriptionFieldsSuccessResponse,
  SubscriptionFieldValue
}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.subscriptions.DevhubAccessRequirement.NoOne
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.subscriptions.{AccessRequirements, DevhubAccessRequirements}
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.PushPullNotificationsService.PushPullNotificationsConnector
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.SubscriptionFieldsService.SubscriptionFieldsConnector
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.AsyncHmrcSpec

class SubscriptionFieldsServiceSpec extends AsyncHmrcSpec with SubscriptionsBuilder with FixedClock with ApplicationWithCollaboratorsFixtures {

  val apiContext: ApiContext       = ApiContext("sub-ser-test")
  val apiVersion: ApiVersionNbr    = ApiVersionNbr("1.0")
  val versionOne: ApiVersionNbr    = ApiVersionNbr("version-1")
  val applicationName: String      = "third-party-application"
  val applicationId: ApplicationId = standardApp.id
  val clientId                     = standardApp.clientId

  val application = standardApp

  trait Setup {

    lazy val locked = false

    implicit val hc: HeaderCarrier = HeaderCarrier()

    val mockConnectorsWrapper: ConnectorsWrapper                           = mock[ConnectorsWrapper]
    val mockThirdPartyApplicationConnector: ThirdPartyApplicationConnector = mock[ThirdPartyApplicationConnector]
    val mockPushPullNotificationsConnector: PushPullNotificationsConnector = mock[PushPullNotificationsConnector]
    val mockSubscriptionFieldsConnector: SubscriptionFieldsConnector       = mock[SubscriptionFieldsConnector]
    val mockApmConnector: ApmConnector                                     = mock[ApmConnector]

    val underTest = new SubscriptionFieldsService(mockConnectorsWrapper, mockApmConnector)

    when(mockConnectorsWrapper.forEnvironment(application.deployedTo))
      .thenReturn(Connectors(mockThirdPartyApplicationConnector, mockSubscriptionFieldsConnector, mockPushPullNotificationsConnector))

    when(
      mockThirdPartyApplicationConnector
        .fetchApplicationById(eqTo(applicationId))(*)
    ).thenReturn(
      Future.successful(
        Some(
          standardApp
        )
      )
    )
  }

  "saveFieldsValues" should {
    "save the fields" in new Setup {
      val developerRole = Collaborator.Roles.DEVELOPER

      val access = AccessRequirements.Default

      val definition1 = buildSubscriptionFieldValue("field1", accessRequirements = access).definition
      val definition2 = buildSubscriptionFieldValue("field2", accessRequirements = access).definition

      val value2 = SubscriptionFieldValue(definition2, FieldValue("oldValue2"))

      val oldValues = Seq(
        SubscriptionFieldValue(definition1, FieldValue("oldValue1")),
        SubscriptionFieldValue(definition2, FieldValue("oldValue2"))
      )

      val newValue1    = FieldValue("newValue")
      val newValuesMap = Map(definition1.name -> newValue1)

      when(mockSubscriptionFieldsConnector.saveFieldValues(*[ClientId], *[ApiContext], *[ApiVersionNbr], *)(*))
        .thenReturn(Future.successful(SaveSubscriptionFieldsSuccessResponse))

      val result = await(underTest.saveFieldValues(developerRole, application, apiContext, apiVersion, oldValues, newValuesMap))

      result shouldBe SaveSubscriptionFieldsSuccessResponse

      val newFields1 = Map(
        definition1.name -> newValue1,
        definition2.name -> value2.value
      )

      verify(mockSubscriptionFieldsConnector)
        .saveFieldValues(eqTo(clientId), eqTo(apiContext), eqTo(apiVersion), eqTo(newFields1))(*)
    }

    "save the fields fails with write access denied" in new Setup {

      val developerRole = Collaborator.Roles.DEVELOPER

      val access = AccessRequirements(devhub = DevhubAccessRequirements(NoOne, NoOne))

      val definition = buildSubscriptionFieldValue("field-denied", accessRequirements = access).definition

      val oldValues = Seq(SubscriptionFieldValue(definition, FieldValue("oldValue")))

      val newValues = Map(definition.name -> FieldValue("newValue"))

      val result = await(underTest.saveFieldValues(developerRole, application, apiContext, apiVersion, oldValues, newValues))

      result shouldBe SaveSubscriptionFieldsAccessDeniedResponse

      verify(mockSubscriptionFieldsConnector, never)
        .saveFieldValues(*[ClientId], *[ApiContext], *[ApiVersionNbr], *)(*)
    }
  }
}
