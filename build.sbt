import Dependencies._

ThisBuild / scalaVersion      := "2.11.12"
ThisBuild / versionScheme     := Some("early-semver")
ThisBuild / organization      := "edu.rit.cs"
ThisBuild / organizationName  := "Rochester Institute of Technology"
ThisBuild / githubOwner       := "michaelmior"
ThisBuild / githubRepository  := "jsonoid-discovery"

inThisBuild(
  List(
    semanticdbEnabled := true,
    semanticdbVersion := scalafixSemanticdb.revision
  )
)

scalafixOnCompile := true
ThisBuild / scalafixDependencies += "net.pixiv" %% "scalafix-pixiv-rule" % "3.0.1"

mainClass := Some("edu.rit.cs.mmior.jsonoid.discovery.DiscoverSchema")

Global / onChangedBuildSource := ReloadOnSourceChanges

val nonConsoleCompilerOptions = Seq(
  "-feature",
  "-Xfatal-warnings",
  "-Ywarn-unused-import",
  "-deprecation",
  "-target:jvm-1.8"
)

val generateSchemas = taskKey[Unit]("Generate example schemas")

generateSchemas := {
  import sbt.Attributed.data

  import java.nio.file.{FileSystems, Files}

  val r = (Compile / runner).value
  val cp = (Compile / fullClasspath).value

  // Generate the output directory to store generated schemas
  val schemaPath = FileSystems.getDefault().getPath("target", "jsonoid-schemas")
  Files.createDirectories(schemaPath)

  val inputs = List(
    "earthquakes.json",
    "gdp.json",
    "mr-robot.json",
    "nobel.json",
    "rickandmorty.json",
    "test.json",
    "jsonlines-example.json"
  )

  for (input <- inputs) {
    r.run("edu.rit.cs.mmior.jsonoid.discovery.DiscoverSchema",
    data(cp),
    Seq(
      "src/test/resources/" + input,
      "-p",
      "Simple",
      "-w",
      schemaPath.resolve(input).toString
    ),
    (streams.value: @sbtUnchecked).log)
  }
}

lazy val root = (project in file("."))
  .settings(
    name := "JSONoid Discovery",
    resolvers += Resolver.githubPackages("michaelmior"),
    resolvers += "jitpack" at "https://jitpack.io",
    libraryDependencies ++= Seq(
        bloomFilter,
        ddSketch,
        json4s,
        json4sScalaz,
        dbscan,
        fuzzySets,
        hyperLogLog,
        jsonSchemaValidator,
        scopt,
        scalaCsv,

        spark % "provided",
        sparkSql % "provided",

        scalactic % Test,
        scalaTest % Test,
    ),
    dependencyOverrides ++= Seq(
      jacksonDatabind,
    ),
    javacOptions ++= Seq("-source", "1.8", "-target", "1.8"),
    scalacOptions ++= nonConsoleCompilerOptions,
    buildInfoKeys := Seq[BuildInfoKey](version),
    buildInfoPackage := "edu.rit.cs.mmior.jsonoid.discovery"
  )

Compile / compile / wartremoverErrors ++= Seq(
  Wart.ArrayEquals,
  Wart.EitherProjectionPartial,
  Wart.Enumeration,
  Wart.Equals,
  Wart.ExplicitImplicitTypes,
  Wart.FinalCaseClass,
  Wart.MutableDataStructures,
  Wart.NonUnitStatements,
  Wart.Null,
  Wart.Option2Iterable,
  Wart.OptionPartial,
  Wart.PublicInference,
  Wart.Recursion,
  Wart.Return,
  Wart.StringPlusAny,
  Wart.TraversableOps,
  Wart.TryPartial,
  Wart.Var,
  Wart.While,
)

Compile / console / scalacOptions := (console / scalacOptions)
  .value.filterNot(opt =>
    nonConsoleCompilerOptions.contains(opt)
)

enablePlugins(BuildInfoPlugin)
enablePlugins(DockerPlugin)
enablePlugins(GitHubPagesPlugin)
enablePlugins(GitVersioning)
enablePlugins(JavaAppPackaging)
enablePlugins(SiteScaladocPlugin)

dockerEntrypoint := Seq("/opt/docker/bin/discover-schema")
dockerBaseImage := "openjdk:8-alpine"

gitHubPagesOrgName := "michaelmior"
gitHubPagesRepoName := "jsonoid-discovery"
gitHubPagesSiteDir := baseDirectory.value / "target/site"

git.remoteRepo := "git@github.com:michaelmior/jsonoid-discovery.git"
git.useGitDescribe := true

Test / fork := true

assembly / assemblyMergeStrategy := {
  case "module-info.class" => MergeStrategy.discard
  case x =>
    val oldStrategy = (assembly / assemblyMergeStrategy).value
    oldStrategy(x)
}
assembly / assemblyJarName       := s"jsonoid-discovery-${version.value}.jar"

import sbtassembly.AssemblyPlugin.defaultUniversalScript
assemblyPrependShellScript := Some(defaultUniversalScript(shebang = false))

run / connectInput := true

// See https://www.scala-sbt.org/1.x/docs/Using-Sonatype.html for instructions on how to publish to Sonatype.
