import sbt.Keys._
import sbt._

import java.nio.file.Paths

ThisBuild / credentials += Credentials(
  "GitHub Package Registry",
  "maven.pkg.github.com",
  "raw-labs",
  sys.env.getOrElse("GITHUB_TOKEN", "")
)

lazy val commonSettings = Seq(
  homepage := Some(url("https://www.raw-labs.com/")),
  organization := "com.raw-labs",
  organizationName := "RAW Labs SA",
  startYear := Some(2023),
  organizationHomepage := Some(url("https://www.raw-labs.com/")),
  developers := List(Developer("raw-labs", "RAW Labs", "engineering@raw-labs.com", url("https://github.com/raw-labs"))),
  licenses := List(
    "Business Source License 1.1" -> new URI(
      "https://raw.githubusercontent.com/raw-labs/snapi/main/licenses/BSL.txt"
    ).toURL
  ),
  headerSources / excludeFilter := HiddenFileFilter,
  // Use cached resolution of dependencies
  // http://www.scala-sbt.org/0.13/docs/Cached-Resolution.html
  updateOptions := updateOptions.in(Global).value.withCachedResolution(true),
  resolvers += "RAW Labs GitHub Package Registry" at "https://maven.pkg.github.com/raw-labs/_",
)

lazy val buildSettings = Seq(
  scalaVersion := "2.12.18",
  javacOptions ++= Seq(
    "-source",
    "21",
    "-target",
    "21"
  ),
  scalacOptions ++= Seq(
    "-feature",
    "-unchecked",
    // When compiling in encrypted drives in Linux, the max size of a name is reduced to around 140
    // https://unix.stackexchange.com/a/32834
    "-Xmax-classfile-name",
    "140",
    "-deprecation",
    "-Xlint:-stars-align,_",
    "-Ywarn-dead-code",
    "-Ywarn-macros:after", // Fix for false warning of unused implicit arguments in traits/interfaces.
    "-Ypatmat-exhaust-depth",
    "160"
  )
)

lazy val compileSettings = Seq(
  Compile / doc / sources := Seq.empty,
  Compile / packageDoc / mappings := Seq(),
  Compile / packageSrc / publishArtifact := true,
  Compile / packageDoc / publishArtifact := false,
  Compile / packageBin / packageOptions += Package.ManifestAttributes(
    "Automatic-Module-Name" -> name.value.replace('-', '.')
  ),
  // Add all the classpath to the module path.
  Compile / javacOptions ++= Seq(
    "--module-path",
    (Compile / dependencyClasspath).value.files.absString
  ),
  // The module-info.java requires the Scala classes to be compiled.
  compileOrder := CompileOrder.ScalaThenJava
)

lazy val testSettings = Seq(
  // Exclude module-info.java, otherwise it will fail the compilation.
  Test / doc / sources := {
    (Compile / doc / sources).value.filterNot(_.getName.endsWith("module-info.java"))
  },
  // Ensuring tests are run in a forked JVM for isolation.
  Test / fork := true,
  // Pass system properties starting with "raw." to the forked JVMs.
  Test / javaOptions ++= {
    import scala.collection.JavaConverters._
    val props = System.getProperties
    props
      .stringPropertyNames()
      .asScala
      .filter(_.startsWith("raw."))
      .map(key => s"-D$key=${props.getProperty(key)}")
      .toSeq
  },
  // Set up heap dump options for out-of-memory errors.
  Test / javaOptions ++= Seq(
    "-XX:+HeapDumpOnOutOfMemoryError",
    s"-XX:HeapDumpPath=${Paths.get(sys.env.getOrElse("SBT_FORK_OUTPUT_DIR", "target/test-results")).resolve("heap-dumps")}"
  ),
  Test / publishArtifact := true,
  Test / packageSrc / publishArtifact := true
)

val isCI = sys.env.getOrElse("CI", "false").toBoolean

lazy val publishSettings = Seq(
  versionScheme := Some("early-semver"),
  publishMavenStyle := true,
  // Temporarily publishing to the Snapi repo until the migration is finished...
  publishTo := Some("GitHub raw-labs Apache Maven Packages" at "https://maven.pkg.github.com/raw-labs/snapi"),
  publishConfiguration := publishConfiguration.value.withOverwrite(isCI)
)

lazy val strictBuildSettings = commonSettings ++ compileSettings ++ buildSettings ++ testSettings ++ Seq(
  scalacOptions ++= Seq(
    "-Xfatal-warnings"
  )
)

lazy val root = (project in file("."))
  .doPatchDependencies() // Patch Scala dependencies to ensure their names are JPMS-friendly.
  .enablePlugins(ProtobufPlugin)
  .settings(
    name := "protocol-compiler",
    strictBuildSettings,
    publishSettings,
     // Set fixed versions
    ProtobufConfig / version := "3.25.4",
    ProtobufConfig / protobufGrpcVersion := "1.62.2",
    // Forcing the dependency so that 'requires' annotation in module-info.java works properly.
    libraryDependencies += "com.google.protobuf" % "protobuf-java" % ((ProtobufConfig / version).value),
    // Include the protobuf files in the JAR
    Compile / unmanagedResourceDirectories += (ProtobufConfig / sourceDirectory).value
  )
