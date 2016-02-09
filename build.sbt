name := "async"

organization := "org.lolczak"

version := "0.2.0"

scalaVersion := "2.11.7"

resolvers += "Tim Tennant's repo" at "http://dl.bintray.com/timt/repo/"

resolvers += "bintray/non" at "http://dl.bintray.com/non/maven"

val akka = Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.3.8"
)

val scalaz = Seq (
  "org.scalaz" %% "scalaz-core" % "7.2.0",
  "org.scalaz" %% "scalaz-concurrent" % "7.2.0"
)

val testLibs = Seq(
  "org.scalatest" %% "scalatest" % "2.2.2" % "test",
  "org.scalacheck" %% "scalacheck" % "1.11.6" % "test",
  "org.mockito" % "mockito-all" % "1.10.19"
)

libraryDependencies ++= scalaz ++ testLibs ++ akka

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