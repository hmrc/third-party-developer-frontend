import sbt._
import play.core.PlayVersion
import play.sbt.PlayImport._

object AppDependencies {
  def apply() : Seq[ModuleID] = compile ++ test

  lazy val cucumberVersion = "6.2.2"
  lazy val seleniumVersion = "2.53.1"
  lazy val enumeratumVersion = "1.5.12"

  val testScope = "test, it, component"

  lazy val compile = Seq(
    ws,
    "uk.gov.hmrc" %% "bootstrap-play-26" % "1.16.0",
    "uk.gov.hmrc" %% "time" % "3.11.0",
    "uk.gov.hmrc" %% "simple-reactivemongo" % "7.31.0-play-26",
    "uk.gov.hmrc" %% "govuk-template" % "5.61.0-play-26",
    "uk.gov.hmrc" %% "play-ui" % "8.21.0-play-26",
    "uk.gov.hmrc" %% "url-builder" % "3.4.0-play-26",
    "uk.gov.hmrc" %% "play-json-union-formatter" % "1.11.0",
    "uk.gov.hmrc" %% "http-metrics" % "1.10.0",
    "uk.gov.hmrc" %% "json-encryption" % "4.8.0-play-26",
    "uk.gov.hmrc" %% "emailaddress" % "3.4.0",
    "uk.gov.hmrc" %% "play-conditional-form-mapping" % "1.4.0-play-26",
    "uk.gov.hmrc" %% "play-frontend-govuk" % "0.49.0-play-26",
    "commons-net" % "commons-net" % "3.6",
    "com.beachape" %% "enumeratum" % enumeratumVersion,
    "com.beachape" %% "enumeratum-play" % enumeratumVersion,
    "com.google.zxing" % "core" % "3.2.1",
    "org.typelevel" %% "cats-core" % "2.0.0",
    "com.typesafe.play" %% "play-json" % "2.8.1",
    "com.typesafe.play" %% "play-json-joda" % "2.8.1"
  )

  lazy val test = Seq(
    "uk.gov.hmrc" %% "reactivemongo-test" % "4.21.0-play-26" % testScope,
    "io.cucumber" %% "cucumber-scala" % cucumberVersion % testScope,
    "io.cucumber" % "cucumber-junit" % cucumberVersion % testScope,
    "io.cucumber" % "cucumber-java8" % cucumberVersion % testScope,
    "junit" % "junit" % "4.12" % testScope,
    "org.jsoup" % "jsoup" % "1.10.2" % testScope,
    "org.pegdown" % "pegdown" % "1.6.0" % testScope,
    "com.typesafe.play" %% "play-test" % PlayVersion.current % testScope,
    "org.scalatestplus.play" %% "scalatestplus-play" % "3.1.3" % testScope,
    "org.seleniumhq.selenium" % "selenium-java" % seleniumVersion % testScope,
    "com.github.tomakehurst" % "wiremock" % "1.58" % testScope,
    "org.mockito" %% "mockito-scala-scalatest" % "1.7.1" % testScope,
    "org.scalaj" %% "scalaj-http" % "2.3.0" % testScope,
    "org.scalacheck" %% "scalacheck" % "1.13.5" % testScope,
    "com.assertthat" % "selenium-shutterbug" % "0.2" % testScope
  )

  lazy val overrideDependencies = Seq(
    "org.seleniumhq.selenium" % "selenium-java" % seleniumVersion % testScope,
    "org.seleniumhq.selenium" % "selenium-htmlunit-driver" % seleniumVersion % testScope,
    "com.typesafe.play" %% "play-json" % "2.8.1",
    "com.typesafe.play" %% "play-json-joda" % "2.8.1"
  )
}
