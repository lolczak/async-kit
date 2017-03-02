name := "async-kit"

organization := "org.lolczak"

version := "0.5.0"

scalaVersion := "2.11.7"

resolvers += "Tim Tennant's repo" at "http://dl.bintray.com/timt/repo/"

resolvers += "bintray/non" at "http://dl.bintray.com/non/maven"

addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.7.1")

val akka = Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.3.8" % Provided
)

val scalaz = Seq (
  "org.scalaz" %% "scalaz-core" % "7.2.0" % Provided,
  "org.scalaz" %% "scalaz-concurrent" % "7.2.0" % Provided
)

val log = Seq(
  "com.typesafe.scala-logging" %% "scala-logging-slf4j" % "2.1.2" % Provided exclude("org.scala-lang", "scala-reflect"),
  "ch.qos.logback" % "logback-classic" % "1.1.2" % Test
)

val testLibs = Seq(
  "org.scalatest" %% "scalatest" % "2.2.2" % Test,
  "org.scalacheck" %% "scalacheck" % "1.11.6" % Test,
  "org.mockito" % "mockito-all" % "1.10.19" % Test
)

libraryDependencies ++= scalaz ++ testLibs ++ akka ++ log

credentials += Credentials("Nexus Repository Manager", "nexus.blocker.vpn", "admin", "admin123")

publishTo := {
  val nexus = "http://nexus.blocker.vpn:8081/"
  if (isSnapshot.value)
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases" at nexus + "content/repositories/releases")
}

publishMavenStyle := true

publishArtifact in Test := false

pomIncludeRepository := { _ => false }