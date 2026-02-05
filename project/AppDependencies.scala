import sbt._
import play.sbt.PlayImport._

object AppDependencies {
  def apply() : Seq[ModuleID] = compile ++ test

  lazy val seleniumVersion = "4.14.0"
  lazy val bootstrapVersion = "9.19.0"
  lazy val mongoVersion = "2.12.0"
  lazy val apiDomainVersion = "0.20.0"
  lazy val appDomainVersion = "0.95.0"
  lazy val tpdDomainVersion = "0.14.0"
  private val orgDomainVersion = "0.11.0"
  private val mockitoScalaVersion = "2.0.0"


  lazy val compile = Seq(
    ws,
    "uk.gov.hmrc"                 %% "bootstrap-frontend-play-30"             % bootstrapVersion,
    "uk.gov.hmrc"                 %% "play-partials-play-30"                  % "10.2.0",
    "uk.gov.hmrc"                 %% "domain-play-30"                         % "11.0.0",
    "uk.gov.hmrc"                 %% "play-frontend-hmrc-play-30"             % "12.29.0",
    "uk.gov.hmrc.mongo"           %% "hmrc-mongo-play-30"                     % mongoVersion,
    "uk.gov.hmrc"                 %% "crypto-json-play-30"                    % "8.4.0",
    "uk.gov.hmrc"                 %% "http-metrics"                           % "2.9.0",
    "uk.gov.hmrc"                 %% "play-conditional-form-mapping-play-30"  % "3.4.0",
    "commons-net"                 %  "commons-net"                            % "3.6",
    "com.google.zxing"            %  "core"                                   % "3.2.1",
    "uk.gov.hmrc"                 %% "api-platform-api-domain"                % apiDomainVersion,
    "uk.gov.hmrc"                 %% "api-platform-application-domain"        % appDomainVersion,
    "uk.gov.hmrc"                 %% "api-platform-tpd-domain"                % tpdDomainVersion,
    "uk.gov.hmrc"                 %% "api-platform-organisation-domain"       % orgDomainVersion
  )

  lazy val test =  Seq(
    "uk.gov.hmrc"                 %% "bootstrap-test-play-30"                   % bootstrapVersion,
    "uk.gov.hmrc.mongo"           %% "hmrc-mongo-test-play-30"                  % mongoVersion,
    "org.mockito"                 %% "mockito-scala-scalatest"                  % mockitoScalaVersion,
    "org.jsoup"                   %  "jsoup"                                    % "1.13.1",
    "org.scalacheck"              %% "scalacheck"                               % "1.17.0",
    "org.scalatestplus"           %% "scalacheck-1-17"                          % "3.2.17.0",
    "uk.gov.hmrc"                 %% "api-platform-test-api-domain"             % apiDomainVersion,
    "uk.gov.hmrc"                 %% "api-platform-application-domain-fixtures" % appDomainVersion,
    "uk.gov.hmrc"                 %% "api-platform-test-tpd-domain"             % tpdDomainVersion
  ).map(_ % "test")

  lazy val componentTestDependencies = Seq(
    "uk.gov.hmrc"                 %% "ui-test-runner"                     % "0.49.0",
  ).map(_ % "test")
}
