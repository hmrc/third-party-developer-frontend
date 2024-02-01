import sbt._
import play.sbt.PlayImport._

object AppDependencies {
  def apply() : Seq[ModuleID] = compile ++ test

  lazy val cucumberVersion = "6.2.2"
  lazy val seleniumVersion = "4.2.0"
  lazy val bootstrapVersion = "7.19.0"
  lazy val mongoVersion = "1.7.0"
  lazy val apiDomainVersion = "0.13.0"
  lazy val appDomainVersion = "0.34.0"

  val testScope = "test, it, component"

  lazy val compile = Seq(
    ws,
    "uk.gov.hmrc"                 %% "bootstrap-frontend-play-28"         % bootstrapVersion,
    "uk.gov.hmrc"                 %% "play-frontend-hmrc"                 % "7.26.0-play-28",
    "uk.gov.hmrc.mongo"           %% "hmrc-mongo-play-28"                 % mongoVersion,
    "uk.gov.hmrc"                 %% "http-metrics"                       % "2.7.0",
    "uk.gov.hmrc"                 %% "crypto-json-play-28"                % "7.3.0",
    "uk.gov.hmrc"                 %% "emailaddress"                       % "3.7.0",
    "uk.gov.hmrc"                 %% "play-conditional-form-mapping"      % "1.12.0-play-28",
    "commons-net"                 %  "commons-net"                        % "3.6",
    "com.google.zxing"            %  "core"                               % "3.2.1",
    "commons-validator"           %  "commons-validator"                  % "1.7",
    "uk.gov.hmrc"                 %% "api-platform-api-domain"            % apiDomainVersion,
    "uk.gov.hmrc"                 %% "api-platform-application-domain"    % appDomainVersion
  )

  lazy val test =  Seq(
    "uk.gov.hmrc"                 %% "bootstrap-test-play-28"             % bootstrapVersion,
    "uk.gov.hmrc.mongo"           %% "hmrc-mongo-test-play-28"            % mongoVersion,
    "org.mockito"                 %% "mockito-scala-scalatest"            % "1.17.29",
    "org.scalatest"               %% "scalatest"                          % "3.2.17",
    "org.jsoup"                   %  "jsoup"                              % "1.13.1",
    "org.scalaj"                  %% "scalaj-http"                        % "2.4.2",
    "com.github.tomakehurst"      %  "wiremock-jre8-standalone"           % "2.33.2",
    "org.scalacheck"              %% "scalacheck"                         % "1.15.4",
    "org.scalatestplus"           %% "scalacheck-1-15"                    % "3.2.10.0",
    "uk.gov.hmrc"                 %% "api-platform-test-api-domain"       % apiDomainVersion
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
