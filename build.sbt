name := "akka-lab"

version := "1.0"

scalaVersion := "2.12.2"

lazy val akkaVersion = "2.5.12"

resolvers += Resolver.jcenterRepo
resolvers += Resolver.bintrayRepo("ovotech", "maven")
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

libraryDependencies += compilerPlugin(
  "org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full
)

libraryDependencies ++= {
  val kafkaSerializationV = "0.3.11" // see the Maven badge above for the latest version
  Seq(
    "com.ovoenergy" %% "kafka-serialization-core" % kafkaSerializationV,
    "com.ovoenergy" %% "kafka-serialization-circe" % kafkaSerializationV, // To provide Circe JSON support
    "com.ovoenergy" %% "kafka-serialization-json4s" % kafkaSerializationV // To provide Json4s JSON support
  )
}

libraryDependencies ++= Seq(
  "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-core" % "0.34.1" % Compile,
  "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-macros" % "0.34.1" % Provided // required only in compile-time
)

libraryDependencies += "com.typesafe.akka" %% "akka-stream-contrib" % "0.6"

mainClass in(Compile, run) := Some("motiv.Boot")

val sampleTask = taskKey[Int]("sample")
sampleTask := {
  println(s"sbtVersion=${sbtVersion.value}")
  0
}