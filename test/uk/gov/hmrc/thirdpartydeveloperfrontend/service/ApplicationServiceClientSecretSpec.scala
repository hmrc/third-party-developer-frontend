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

import java.time.LocalDateTime
import java.util.UUID
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._

import scala.concurrent.Future.{failed, successful}

class ApplicationServiceClientSecretSpec extends ApplicationServiceCommonSetup {

  trait Setup extends CommonSetup

  "addClientSecret" should {
    val newClientSecretId = UUID.randomUUID().toString
    val newClientSecret = UUID.randomUUID().toString
    val actor = CollaboratorActor("john.requestor@example.com")
    val timestamp = LocalDateTime.now(clock)

    "add a client secret for app in production environment" in new Setup {

      theProductionConnectorthenReturnTheApplication(productionApplicationId, productionApplication)

      when(mockProductionApplicationConnector.addClientSecrets(productionApplicationId, ClientSecretRequest(actor, timestamp)))
        .thenReturn(successful((newClientSecretId, newClientSecret)))

      private val updatedToken = await(applicationService.addClientSecret(productionApplication, actor))

      updatedToken._1 shouldBe newClientSecretId
      updatedToken._2 shouldBe newClientSecret
    }

    "propagate exceptions from connector" in new Setup {

      theProductionConnectorthenReturnTheApplication(productionApplicationId, productionApplication)

      when(mockProductionApplicationConnector.addClientSecrets(productionApplicationId, ClientSecretRequest(actor, timestamp)))
        .thenReturn(failed(new ClientSecretLimitExceeded))

      intercept[ClientSecretLimitExceeded] {
        await(applicationService.addClientSecret(productionApplication, actor))
      }
    }
  }

  "deleteClientSecret" should {
    val applicationId = ApplicationId.random
    val actor = CollaboratorActor("john.requestor@example.com")
    val secretToDelete = UUID.randomUUID().toString
    val timestamp = LocalDateTime.now(clock)

    "delete a client secret" in new Setup {

      val application = productionApplication.copy(id = applicationId)
      val removeClientSecretRequest = RemoveClientSecret(actor, secretToDelete, timestamp)

      theProductionConnectorthenReturnTheApplication(applicationId, application)

      when(mockProductionApplicationConnector.applicationUpdate(eqTo(applicationId), eqTo(removeClientSecretRequest))(*))
        .thenReturn(successful(ApplicationUpdateSuccessful))

      await(applicationService.deleteClientSecret(application, actor, secretToDelete)) shouldBe ApplicationUpdateSuccessful
    }
  }
}
