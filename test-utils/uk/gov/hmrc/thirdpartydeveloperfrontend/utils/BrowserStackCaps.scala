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

package uk.gov.hmrc.thirdpartydeveloperfrontend.utils

trait BrowserStackCaps {

  val browserStackCaps    = List("browserstack.debug" -> "true", "browserstack.local" -> "true")

  // OS for Windows
  val windowsXPCaps       = List("os" -> "Windows", "os_version" -> "XP") ++ browserStackCaps
  val windows7Caps        = List("os" -> "Windows", "os_version" -> "7") ++ browserStackCaps
  val windows8Caps        = List("os" -> "Windows", "os_version" -> "8") ++ browserStackCaps
  val windows81Caps       = List("os" -> "Windows", "os_version" -> "8.1") ++ browserStackCaps
  val windows10Caps       = List("os" -> "Windows", "os_version" -> "10") ++ browserStackCaps

  // OS for MAC OS-X
  val macosxMavericksCaps = List("os" -> "OS X", "os_version" -> "Mavericks") ++ browserStackCaps
  val macosxYosemiteCaps  = List("os" -> "OS X", "os_version" -> "Yosemite") ++ browserStackCaps
  val macosxElCapitanCaps = List("os" -> "OS X", "os_version" -> "El Capitan") ++ browserStackCaps
  val macosxSierraCaps = List("os" -> "OS X", "os_version" -> "Sierra") ++ browserStackCaps

  //platform for browsername ios mobile devices
  val iosIphoneCaps = List("browserName" -> "iPhone", "platform" -> "MAC") ++ browserStackCaps
  val androidCaps = List("browserName" -> "android", "platform" -> "ANDROID") ++ browserStackCaps

  //platform for browsername ios ipad devices
  val iosIpadRetinaCaps = List("browserName" -> "iPad", "platform" -> "MAC") ++ browserStackCaps

  val win7ie8Caps = List("browser" -> "IE", "browser_version" -> "8.0") ++ windows7Caps
  val win7ie9Caps = List("browser" -> "IE", "browser_version" -> "9.0") ++ windows7Caps
  val win7ie10Caps = List("browser" -> "IE", "browser_version" -> "10.0") ++ windows7Caps
  val win7ie11Caps = List("browser" -> "IE", "browser_version" -> "11.0") ++ windows7Caps
  val win8ie10Caps = List("browser" -> "IE", "browser_version" -> "10.0") ++ windows8Caps
  val win81ie11Caps = List("browser" -> "IE", "browser_version" -> "11.0") ++ windows81Caps
  val win10ie11Caps = List("browser" -> "IE", "browser_version" -> "11.0") ++ windows10Caps
  val win10edgeCaps = List("browser" -> "Edge", "browser_version" -> "13.0") ++ windows10Caps

  //Browsers and OS for windows and chrome
  val win10chromeLatestCaps = List("browser" -> "Chrome", "browser_version" -> "54.0") ++ windows10Caps
  val win8chromeLatestCaps = List("browser" -> "Chrome", "browser_version" -> "54.0") ++ windows8Caps
  val win81chromeLatestCaps = List("browser" -> "Chrome", "browser_version" -> "54.0") ++ windows81Caps
  val win7firefox46Caps = List("browser" -> "Firefox", "browser_version" -> "46.0") ++  windows7Caps

  //Browsers and OS for windows and firefox
  val win10firefoxLatestCaps = List("browser" -> "Firefox", "browser_version" -> "47.0") ++ windows10Caps
  val win10firefoxVersion46 = List("browser" -> "Firefox", "browser_version" -> "46.0") ++ windows10Caps

  val win8firefox33Caps = List("browser" -> "Firefox", "browser_version" -> "33.0") ++ windows8Caps
  val win81firefox33Caps = List("browser" -> "Firefox", "browser_version" -> "33.0") ++ windows81Caps

  //Browsers and OS for MAC OS-X, chrome, firefox and safari
  val macosxElCapitanSafar91Caps = List("browser" -> "Safari", "browser_version" -> "9.1") ++ macosxElCapitanCaps
  val macosxSierraSafa10Caps = List("browser" -> "Safari", "browser_version" -> "10.0") ++ macosxSierraCaps
  val macosxMavericksSafar7Caps = List("browser" -> "Safari", "browser_version" -> "7.0") ++ macosxMavericksCaps
  val macosxYosemiteSafar8Caps = List("browser" -> "Safari", "browser_version" -> "8.0") ++ macosxYosemiteCaps
  val macosxYosemitechromeLatestCaps = List("browser" -> "Chrome", "browser_version" -> "54.0") ++ macosxYosemiteCaps
  val macosxYosemitefirefoxLatestCaps = List("browser" -> "Firefox", "browser_version" -> "47.0") ++ macosxYosemiteCaps

  //Browsername,platform for IOS mobile Devices
  val iosIphone5SCaps = List("device" -> "iPhone 5S") ++ iosIphoneCaps
  val iosIphone6SCaps = List("device" -> "iPhone 6S Plus") ++ iosIphoneCaps

  //Browsername,platform for IOS ipad Devices
  val iosIpadMiniRetinaCaps = List("device" -> "iPad mini", "emulator" -> "false") ++ iosIpadRetinaCaps
  val iosIpad4thGenCaps = List("device" -> "iPad 4th Gen") ++ iosIpadRetinaCaps
  val iosIpadAirCaps = List("device" -> "iPad Air", "emulator" -> "true") ++ iosIpadRetinaCaps

  //Browsername,platform for Android Devices
  val androidSamsungGalaxyS5Caps = List("device" -> "Samsung Galaxy S5") ++ androidCaps
  val androidSamsungGalaxyS4Caps = List("device" -> "Samsung Galaxy S4") ++ androidCaps
  val androidSamsungGalaxyNote2Caps = List("device" -> "Samsung Galaxy Note 2") ++ androidCaps
  val androidSamsungTab4Caps = List("device" -> "Samsung Galaxy Tab 4 10.1") ++ androidCaps
  val htcOnem8Caps = List("device" -> "HTC One M8") ++ androidCaps
  val amazonKindleFire2 = List("device" -> "Amazon Kindle Fire 2") ++ androidCaps
  val amazonKindleFireHDX7 = List("device" -> "Amazon Kindle Fire HDX 7") ++ androidCaps

}
