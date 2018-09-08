name := "akka-lab"

version := "1.0"

scalaVersion := "2.12.2"

lazy val akkaVersion = "2.5.12"

resolvers += Resolver.jcenterRepo

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  // https://mvnrepository.com/artifact/com.typesafe.akka/akka-http_2.11
  "com.typesafe.akka" % "akka-http_2.12" % "10.0.9",
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion,
  "com.github.dnvriend" %% "akka-persistence-inmemory" % "2.5.1.1",
  "org.scalatest" %% "scalatest" % "3.0.1" % "test",
  "com.typesafe.akka" %% "akka-stream" % akkaVersion,
  "org.scalatest" %% "scalatest" % "3.2.0-SNAP9",
  "joda-time" % "joda-time" % "2.7",
  "com.typesafe.akka" %% "akka-stream-kafka" % "0.22"
)

val circeVersion = "0.9.3"

libraryDependencies ++= Seq(
  "io.circe" %% "circe-core",
  "io.circe" %% "circe-generic",
  "io.circe" %% "circe-parser"
).map(_ % circeVersion)

libraryDependencies += "com.typesafe.akka" %% "akka-slf4j" % akkaVersion
libraryDependencies ++= Seq(
  "io.kamon" %% "kamon-core" % "1.1.0",
  "io.kamon" %% "kamon-logback" % "1.0.0",
  "io.kamon" %% "kamon-akka-2.5" % "1.0.1",
  "io.kamon" %% "kamon-prometheus" % "1.0.0"
)

libraryDependencies +=compilerPlugin(
  "org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full
)

mainClass in (Compile, run) := Some("motiv.Boot")