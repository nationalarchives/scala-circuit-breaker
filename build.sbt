import sbt.Keys.resolvers
import ReleaseTransformations._

ThisBuild / versionScheme := Some("semver-spec")

lazy val root = Project("scala-circuit-breaker", file("."))
  .configs(IntegrationTest)
  .settings(
    Defaults.itSettings,
    organization := "uk.gov.nationalarchives",
    name := "scala-circuit-breaker",
    version := "1.0.0-SNAPSHOT",
    scalaVersion := "2.13.8",
    licenses := Seq("MIT" -> url("https://opensource.org/licenses/MIT")),
    homepage := Some(url("https://github.com/nationalarchives/scala-circuit-breaker")),
    startYear := Some(2022),
    description := "The Circuit Breaker pattern implemented as a library in Scala",
    organizationName := "The National Archives",
    organizationHomepage := Some(url("http://nationalarchives.gov.uk")),
    scmInfo := Some(ScmInfo(
      url("https://github.com/nationalarchives/scala-circuit-breaker"),
      "scm:git@github.com:nationalarchives/scala-circuit-breaker.git")
    ),
    developers := List(
      Developer(
        id = "adamretter",
        name = "Adam Retter",
        email = "adam@evolvedbinary.com",
        url = url("https://www.evolvedbinary.com")
      )
    ),
    scalacOptions ++= Seq(
        "-target:jvm-1.8",
        "-encoding", "utf-8",
    ),
    resolvers ++= Seq(
      Resolver.mavenLocal
    ),
    headerLicense := Some(HeaderLicense.MIT("2022", "The National Archives")),
    libraryDependencies ++= Seq(
      "net.jcip" % "jcip-annotations" % "1.0",
      "org.scalatest" %% "scalatest" % "3.2.10" % Test
    ),
    dependencyCheckArchiveAnalyzerEnabled := Some(false),
    dependencyCheckAssemblyAnalyzerEnabled := Some(false),
    dependencyCheckAutoconfAnalyzerEnabled := Some(false),
    dependencyCheckCmakeAnalyzerEnabled := Some(false),
    dependencyCheckCocoapodsEnabled := Some(false),
    dependencyCheckNodeAnalyzerEnabled := Some(false),
    dependencyCheckNodeAuditAnalyzerEnabled := Some(false),
    dependencyCheckNexusAnalyzerEnabled := Some(false),
    dependencyCheckNuspecAnalyzerEnabled := Some(false),
    dependencyCheckNugetConfAnalyzerEnabled := Some(false),
    dependencyCheckPyDistributionAnalyzerEnabled := Some(false),
    dependencyCheckPyPackageAnalyzerEnabled := Some(false),
    dependencyCheckPyPackageAnalyzerEnabled := Some(false),
    dependencyCheckRubygemsAnalyzerEnabled := Some(false),
    dependencyCheckRetireJSAnalyzerEnabled := Some(false),
    dependencyCheckSwiftEnabled := Some(false),

    publishMavenStyle := true,
    credentials += Credentials(Path.userHome / ".ivy2" / ".credentials"),
    publishTo := {
      val nexus = "https://oss.sonatype.org/"
      if (isSnapshot.value)
        Some("snapshots" at nexus + "content/repositories/snapshots/")
      else
        Some("releases"  at nexus + "service/local/staging/deploy/maven2/")
    },

    releaseCrossBuild := false,
    releaseProcess := Seq[ReleaseStep](
      checkSnapshotDependencies,
      inquireVersions,
      runClean,
      runTest,
      setReleaseVersion,
      commitReleaseVersion,
      tagRelease,
      releaseStepCommand("publishSigned"),
      setNextVersion,
      commitNextVersion,
      pushChanges
    )
  )
