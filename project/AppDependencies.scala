import sbt._
import play.core.PlayVersion
import play.sbt.PlayImport._

object AppDependencies {
  def apply() : Seq[ModuleID] = compile ++ test

  lazy val cucumberVersion = "6.2.2"
  lazy val seleniumVersion = "4.2.0"
  lazy val enumeratumVersion = "1.7.2"
  lazy val bootstrapVersion = "7.15.0"
  lazy val mongoVersion = "0.74.0"

  val testScope = "test, it, component"

  lazy val compile = Seq(
    ws,
    "uk.gov.hmrc"                 %% "bootstrap-frontend-play-28"         % bootstrapVersion,
    "uk.gov.hmrc"                 %% "play-frontend-hmrc"                 % "6.2.0-play-28",
    "uk.gov.hmrc.mongo"           %% "hmrc-mongo-play-28"                 % mongoVersion,
    "uk.gov.hmrc"                 %% "http-metrics"                       % "2.7.0",
    "uk.gov.hmrc"                 %% "json-encryption"                    % "5.1.0-play-28",
    "uk.gov.hmrc"                 %% "emailaddress"                       % "3.7.0",
    "uk.gov.hmrc"                 %% "play-conditional-form-mapping"      % "1.12.0-play-28",
    "commons-net"                 %  "commons-net"                        % "3.6",
    "com.beachape"                %% "enumeratum"                         % enumeratumVersion,
    "com.beachape"                %% "enumeratum-play"                    % enumeratumVersion,
    "com.google.zxing"            %  "core"                               % "3.2.1",
    "com.typesafe.play"           %% "play-json"                          % "2.9.3",
    "commons-validator"           %  "commons-validator"                  % "1.7",
    "uk.gov.hmrc"                 %% "api-platform-application-commands"  % "0.15.0"
  )

  lazy val test =  Seq(
    "uk.gov.hmrc"                 %% "bootstrap-test-play-28"             % bootstrapVersion,
    "uk.gov.hmrc.mongo"           %% "hmrc-mongo-test-play-28"            % mongoVersion,
    "org.mockito"                 %% "mockito-scala-scalatest"            % "1.17.12",
    "org.jsoup"                   %  "jsoup"                              % "1.13.1",
    "org.scalaj"                  %% "scalaj-http"                        % "2.4.2",
    "com.github.tomakehurst"      %  "wiremock-jre8-standalone"           % "2.33.2",
    "org.scalacheck"              %% "scalacheck"                         % "1.15.4",
    "org.scalatestplus"           %% "scalacheck-1-15"                    % "3.2.10.0"
  ).map(_ % testScope) ++   
  Seq(    
    "io.cucumber"                 %% "cucumber-scala"                     % cucumberVersion,
    "io.cucumber"                 %  "cucumber-junit"                     % cucumberVersion,
    "io.cucumber"                 %  "cucumber-java8"                     % cucumberVersion,
    "org.seleniumhq.selenium"     %  "selenium-remote-driver"             % seleniumVersion,
    "org.seleniumhq.selenium"     %  "selenium-firefox-driver"            % seleniumVersion,
    "org.seleniumhq.selenium"     %  "selenium-chrome-driver"             % seleniumVersion,
    "org.scalatestplus"           %% "selenium-4-2"                       % "3.2.13.0",
    "com.github.tomakehurst"      %  "wiremock-jre8-standalone"           % "2.33.2",
    "uk.gov.hmrc"                 %% "webdriver-factory"                  % "0.38.0"
  ).map(_ % "component")
}
