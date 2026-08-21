import sbt._
import play.sbt.PlayImport._

object AppDependencies {
  def apply(): Seq[ModuleID] = compile ++ test

  lazy val seleniumVersion        = "4.14.0"
  lazy val bootstrapVersion       = "10.7.0"
  lazy val mongoVersion           = "2.13.0"
  lazy val commonDomainVersion    = "1.3.0"
  lazy val apiDomainVersion       = "1.8.0"
  lazy val appDomainVersion       = "1.6.0"
  lazy val tpdDomainVersion       = "1.3.0"
  private val orgDomainVersion    = "1.9.0"
  private val mockitoScalaVersion = "2.2.1"

  lazy val compile = Seq(
    ws,
    "uk.gov.hmrc"       %% "bootstrap-frontend-play-30"            % bootstrapVersion,
    "uk.gov.hmrc"       %% "play-partials-play-30"                 % "10.2.0",
    "uk.gov.hmrc"       %% "play-frontend-hmrc-play-30"            % "13.11.0",
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-play-30"                    % mongoVersion,
    "uk.gov.hmrc"       %% "crypto-json-play-30"                   % "8.4.0",
    "uk.gov.hmrc"       %% "play-conditional-form-mapping-play-30" % "3.5.0",
    "commons-net"        % "commons-net"                           % "3.6",
    "com.google.zxing"   % "core"                                  % "3.2.1",
    "uk.gov.hmrc"       %% "api-platform-common-domain"            % commonDomainVersion,
    "uk.gov.hmrc"       %% "api-platform-api-domain"               % apiDomainVersion,
    "uk.gov.hmrc"       %% "api-platform-application-domain"       % appDomainVersion,
    "uk.gov.hmrc"       %% "api-platform-tpd-domain"               % tpdDomainVersion,
    "uk.gov.hmrc"       %% "api-platform-organisation-domain"      % orgDomainVersion
  )

  lazy val test = Seq(
    "uk.gov.hmrc"       %% "bootstrap-test-play-30"                    % bootstrapVersion,
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-test-play-30"                   % mongoVersion,
    "org.mockito"       %% "mockito-scala-scalatest"                   % mockitoScalaVersion,
    "org.jsoup"          % "jsoup"                                     % "1.22.1",
    "org.scalacheck"    %% "scalacheck"                                % "1.17.0",
    "org.scalatestplus" %% "scalacheck-1-17"                           % "3.2.17.0",
    "uk.gov.hmrc"       %% "api-platform-common-domain-fixtures"       % commonDomainVersion,
    "uk.gov.hmrc"       %% "api-platform-api-domain-fixtures"          % apiDomainVersion,
    "uk.gov.hmrc"       %% "api-platform-application-domain-fixtures"  % appDomainVersion,
    "uk.gov.hmrc"       %% "api-platform-tpd-domain-fixtures"          % tpdDomainVersion,
    "uk.gov.hmrc"       %% "api-platform-organisation-domain-fixtures" % orgDomainVersion
  ).map(_ % "test")

  lazy val componentTestDependencies = Seq(
    "uk.gov.hmrc" %% "ui-test-runner" % "0.49.0"
  ).map(_ % "test")
}
