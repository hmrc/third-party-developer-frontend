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

package uk.gov.hmrc.thirdpartydeveloperfrontend.utils

import scala.reflect.ClassTag

import org.mockito.{MockMakers, Mockito}

// Builds mocks by generating a real subclass, rather than Mockito's default of rewriting the class in place.
// The default can't stub methods a class inherits from a trait - it ignores the stub and runs the real code.
trait SubclassMockSupport {

  def subclassMock[T](using ct: ClassTag[T]): T =
    Mockito.mock(
      ct.runtimeClass.asInstanceOf[Class[T]],
      Mockito.withSettings().defaultAnswer(org.mockito.stubbing.ReturnsSmartNulls).mockMaker(MockMakers.SUBCLASS)
    )
}
