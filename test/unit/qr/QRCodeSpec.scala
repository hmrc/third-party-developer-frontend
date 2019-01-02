/*
 * Copyright 2019 HM Revenue & Customs
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

package unit.qr

import qr.QRCode
import uk.gov.hmrc.play.test.UnitSpec

class QRCodeSpec extends UnitSpec {

  "generateDataImageBase64" should {
    "generate a base64 encoded image of a QR code" in {
      QRCode().generateDataImageBase64("Test") shouldBe "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABcAAAAXCAIAAABvSEP3AAAAnklEQVR42q2TUQ7AIAhDuf+lt+wLLY+qcSYzmkHBtsTzx4rvo5UREeOOkRkxYc+ZskNkrYP1BV0jzT/T3QEKvH+JgpWR6ZYXZr7hiDVqXRBx7JcxTXQZA+ScKL55Y5kJpVrW+IW523cKQoDShosqvPbiNcLnHMw0HlhTw4VQa66LaTSkqF/8NErlWmarFzPiuzONdADNy5nuMlWj+/UCt7Jrv4IzAM8AAAAASUVORK5CYII="
    }
    "generate a base64 encoded image of a barcode from the supplied text with the specified scale" in {
      QRCode(5).generateDataImageBase64("QRCode test text") shouldBe "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAHMAAABzCAIAAAAkIaqxAAABdUlEQVR42u3aUQ6DQAgFQO9/6fYIjREe2B1+NV0Zm4DsXh/RExcCsmQF2ajslYofj1X3y1MZkSVLlizZUdnKWnkn/9i6jRmRJUuWLNlFsoU1+kn+hTc3ZkSWLFmyZI+Q7SvoZMmSJUuWbLA36Kv+ZMmSJUv2SNmp+Wyf++mTb7JkyZL9W9nciZLU1eNOH5ElS5bsKbJTEdudzGVElixZsmSjsrFNwzdOYO89M1myZMmSXST7ZKUl0H0NDFmyZMmSHZV9xeizr5/JzWfJkiVLlmzvDmNfuV/CUZgvWbJkyZL9F9lCnSUNDFmyZMmSXSy784zMklf46IwMWbJkyZLtlZ0aue78ut1yRoYsWbJkyd6Wnar+sb6icaZMlixZsmQXyfZV/yUtSuUrJEuWLFmyR8guWbdyTEyWLFmyZI+QXbI7WflvIEuWLFmyi2T78o/V99xCZMmSJUt2UnbqjEzhMDf2OUuWLFmyZEdlRVkvhIAsWUG2Nb7BsxueT39dvwAAAABJRU5ErkJggg=="
    }
  }
}
