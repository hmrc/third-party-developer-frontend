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
    "uk.gov.hmrc"                 %% "bootstrap-frontend-play-26"     % "5.16.0",
    "uk.gov.hmrc"                 %% "time"                           % "3.25.0",
    "uk.gov.hmrc"                 %% "simple-reactivemongo"           % "7.31.0-play-26",
    "uk.gov.hmrc"                 %% "govuk-template"                 % "5.72.0-play-26",
    "uk.gov.hmrc"                 %% "play-ui"                        % "9.7.0-play-26",
    "uk.gov.hmrc"                 %% "url-builder"                    % "3.4.0-play-26",
    "uk.gov.hmrc"                 %% "play-json-union-formatter"      % "1.15.0-play-26",
    "uk.gov.hmrc"                 %% "http-metrics"                   % "2.3.0-play-26",
    "uk.gov.hmrc"                 %% "json-encryption"                % "4.8.0-play-26",
    "uk.gov.hmrc"                 %% "emailaddress"                   % "3.4.0",
    "uk.gov.hmrc"                 %% "play-conditional-form-mapping"  % "1.4.0-play-26",
    "uk.gov.hmrc"                 %% "play-frontend-govuk"            % "0.63.0-play-26",
    "uk.gov.hmrc"                 %% "play-frontend-hmrc"             % "1.9.0-play-26",
    "commons-net"                 %  "commons-net"                    % "3.6",
    "com.beachape"                %% "enumeratum"                     % enumeratumVersion,
    "com.beachape"                %% "enumeratum-play"                % enumeratumVersion,
    "com.google.zxing"            %  "core"                           % "3.2.1",
    "org.typelevel"               %% "cats-core"                      % "2.0.0",
    "com.typesafe.play"           %% "play-json"                      % "2.9.2",
    "com.typesafe.play"           %% "play-json-joda"                 % "2.9.2"
  )

  lazy val test =  Seq(
    "uk.gov.hmrc"                 %% "bootstrap-test-play-26"         % "5.16.0",
    "uk.gov.hmrc"                 %% "reactivemongo-test"             % "4.21.0-play-26",
    "org.mockito"                 %% "mockito-scala-scalatest"        % "1.7.1", // "1.16.42",
    "org.jsoup"                   %  "jsoup"                          % "1.10.2",
    "org.pegdown"                 %  "pegdown"                        % "1.6.0",
    "org.scalaj"                  %% "scalaj-http"                    % "2.3.0",
    "com.github.tomakehurst"      %  "wiremock-jre8-standalone"       % "2.31.0",
    "org.scalacheck"              %% "scalacheck"                     % "1.13.5"
  ).map(_ % testScope) ++
  Seq(
    "io.cucumber"                 %% "cucumber-scala"                 % cucumberVersion,
    "io.cucumber"                 %  "cucumber-junit"                 % cucumberVersion,
    "io.cucumber"                 %  "cucumber-java8"                 % cucumberVersion,
    "org.seleniumhq.selenium"     %  "selenium-java"                  % seleniumVersion,
    "com.assertthat"              %  "selenium-shutterbug"            % "0.2"
  ).map(_ % "component")
}
