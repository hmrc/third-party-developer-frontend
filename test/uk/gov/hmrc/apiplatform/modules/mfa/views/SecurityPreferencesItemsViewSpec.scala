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

package uk.gov.hmrc.apiplatform.modules.mfa.views

import java.time.format.DateTimeFormatter
import java.time.{Instant, LocalDate, ZoneOffset}

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.scalatest.Assertion
import views.helper.CommonViewSpec

import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest

import uk.gov.hmrc.apiplatform.modules.common.services.DateTimeHelper.LocalDateConversionSyntax
import uk.gov.hmrc.apiplatform.modules.mfa.views.html.SecurityPreferencesItemsView
import uk.gov.hmrc.apiplatform.modules.tpd.test.builders.UserBuilder
import uk.gov.hmrc.apiplatform.modules.tpd.mfa.domain.models.{AuthenticatorAppMfaDetail, MfaDetail, MfaId, SmsMfaDetail}
import uk.gov.hmrc.apiplatform.modules.tpd.test.utils.LocalUserIdTracker
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithCSRFAddToken

class SecurityPreferencesItemsViewSpec extends CommonViewSpec with WithCSRFAddToken {
  implicit val request: FakeRequest[AnyContentAsEmpty.type]      = FakeRequest()
  val securityPreferencesItemsView: SecurityPreferencesItemsView = app.injector.instanceOf[SecurityPreferencesItemsView]

  val authAppMfaDetail: AuthenticatorAppMfaDetail =
    AuthenticatorAppMfaDetail(MfaId(java.util.UUID.randomUUID()), "name", LocalDate.of(2022, 9, 1).asInstant, verified = true)

  val smsMfaDetail: SmsMfaDetail =
    SmsMfaDetail(MfaId(java.util.UUID.randomUUID()), "name", LocalDate.of(2022, 9, 1).asInstant, mobileNumber = "1234567890", verified = true)

  "SecurityPreferencesItems view" should {

    "show 'auth app row' when list contains only auth app mfa details with created on after migration date" in {
      val mainView = securityPreferencesItemsView.render(List(authAppMfaDetail))
      val document = Jsoup.parse(mainView.body)
      document.getElementById("description").text shouldBe "This is how you get your access codes."
      verifyMfaRow(document, authAppMfaDetail, 0, shouldShowCreatedDate = true)
    }

    "show 'auth app row' when list contains only auth app mfa details with created on before migration date" in {
      val authAppMfaDetailWithCreatedOnInPast = authAppMfaDetail.copy(createdOn = LocalDate.of(2022, 7, 20).asInstant)
      val mainView                            = securityPreferencesItemsView.apply(List(authAppMfaDetailWithCreatedOnInPast))()
      val document                            = Jsoup.parse(mainView.body)
      verifyMfaRow(document, authAppMfaDetailWithCreatedOnInPast, 0, shouldShowCreatedDate = false)
    }

    "show 'sms detail row' when list contains only sms mfa details with created on after migration date" in {
      val mainView = securityPreferencesItemsView.apply(List(smsMfaDetail))()
      val document = Jsoup.parse(mainView.body)
      document.getElementById("description").text shouldBe "This is how you get your access codes."
      verifyMfaRow(document, smsMfaDetail, 0, shouldShowCreatedDate = true)
    }

    "show 'sms detail row' and 'auth app row' when list contains auth app and sms mfa details with created on after migration date" in {
      val mainView = securityPreferencesItemsView.apply(List(authAppMfaDetail, smsMfaDetail))()
      val document = Jsoup.parse(mainView.body)
      document.getElementById("description").text shouldBe "This is how you get your access codes."
      verifyMfaRow(document, authAppMfaDetail, 0, shouldShowCreatedDate = true)
      verifyMfaRow(document, smsMfaDetail, 1, shouldShowCreatedDate = true)
    }
  }

  def verifyMfaRow(document: Document, mfaDetail: MfaDetail, rowId: Int, shouldShowCreatedDate: Boolean): Assertion = {
    document.getElementById("description").text shouldBe "This is how you get your access codes."
    val mfaTypeField = Option(document.getElementById(s"mfaType-$rowId"))
    mfaTypeField should not be None
    mfaTypeField.get.text shouldBe mfaDetail.mfaType.displayText

    if (shouldShowCreatedDate) {
      document.getElementById(s"date-hint-$rowId").text shouldBe s"Added ${DateTimeFormatter.ofPattern("dd MMMM yyyy HH:mm").withZone(ZoneOffset.UTC).format(mfaDetail.createdOn)}"
    } else {
      Option(document.getElementById(s"date-hint-$rowId")) shouldBe None
    }
    document.getElementById(s"removeMfaLink-$rowId").text shouldBe "Remove"

  }
}
