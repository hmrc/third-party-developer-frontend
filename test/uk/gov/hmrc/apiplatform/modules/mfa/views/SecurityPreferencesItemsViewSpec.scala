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

package uk.gov.hmrc.apiplatform.modules.mfa.views

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import play.api.test.FakeRequest
import uk.gov.hmrc.apiplatform.modules.mfa.models.{AuthenticatorAppMfaDetailSummary, MfaDetail, MfaId, SmsMfaDetailSummary}
import uk.gov.hmrc.apiplatform.modules.mfa.views.html.SecurityPreferencesItemsView
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.DeveloperBuilder
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.{LocalUserIdTracker, WithCSRFAddToken}
import views.helper.CommonViewSpec

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter


class SecurityPreferencesItemsViewSpec extends CommonViewSpec with WithCSRFAddToken with DeveloperBuilder with LocalUserIdTracker {
  implicit val request = FakeRequest()
  val securityPreferencesItemsView = app.injector.instanceOf[SecurityPreferencesItemsView]
  val authAppMfaDetail = AuthenticatorAppMfaDetailSummary(MfaId(java.util.UUID.randomUUID()), "name", LocalDateTime.of(2022, 9, 1, 0, 0), verified = true)
  val smsMfaDetail = SmsMfaDetailSummary(MfaId(java.util.UUID.randomUUID()), "name", LocalDateTime.of(2022, 9, 1, 0, 0), mobileNumber = "1234567890", verified = true)

  "SecurityPreferencesItems view" should {


    "show 'auth app row' when list contains only auth app mfa details with created on after migration date" in {
      val mainView = securityPreferencesItemsView.render(List(authAppMfaDetail))
     val document = Jsoup.parse(mainView.body)
      document.getElementById("description").text shouldBe "This is how you get your access codes."
      verifyMfaRow(document, authAppMfaDetail, 0, shouldShowCreatedDate = true)
    }

    "show 'auth app row' when list contains only auth app mfa details with created on before migration date" in {
      val authAppMfaDetailWithCreatedOnInPast = authAppMfaDetail.copy(createdOn = LocalDateTime.of(2022,7,20,0,0))
      val mainView = securityPreferencesItemsView.apply(List(authAppMfaDetailWithCreatedOnInPast))
      val document = Jsoup.parse(mainView.body)
      verifyMfaRow(document, authAppMfaDetailWithCreatedOnInPast, 0, shouldShowCreatedDate = false)
    }

    "show 'sms detail row' when list contains only sms mfa details with created on after migration date" in {
      val mainView = securityPreferencesItemsView.apply(List(smsMfaDetail))
      val document = Jsoup.parse(mainView.body)
      document.getElementById("description").text shouldBe "This is how you get your access codes."
      verifyMfaRow(document, smsMfaDetail, 0, shouldShowCreatedDate = true)
    }

    "show 'sms detail row' and 'auth app row' when list contains auth app and sms mfa details with created on after migration date" in {
      val mainView = securityPreferencesItemsView.apply(List(authAppMfaDetail,smsMfaDetail))
      val document = Jsoup.parse(mainView.body)
      document.getElementById("description").text shouldBe "This is how you get your access codes."
      verifyMfaRow(document, authAppMfaDetail, 0, shouldShowCreatedDate = true)
      verifyMfaRow(document, smsMfaDetail, 1, shouldShowCreatedDate = true)
    }
  }

  def verifyMfaRow(document: Document, mfaDetail: MfaDetail, rowId: Int, shouldShowCreatedDate: Boolean) ={
    document.getElementById("description").text shouldBe "This is how you get your access codes."
    val mfaTypeField = Option(document.getElementById(s"mfaType-$rowId"))
    mfaTypeField should not be None
    mfaTypeField.get.text shouldBe mfaDetail.mfaType.asText
    if(shouldShowCreatedDate){
      document.getElementById(s"date-hint-$rowId").text shouldBe s"Added ${DateTimeFormatter.ofPattern("dd MMMM yyyy HH:mm").format(mfaDetail.createdOn)}"
    } else {
      Option(document.getElementById(s"date-hint-$rowId")) shouldBe None
    }
    document.getElementById(s"removeMfaLink-$rowId").text shouldBe "Remove"

  }
}
