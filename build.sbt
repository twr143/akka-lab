name := "akka-lab"

version := "1.0"

scalaVersion := "2.12.2"

lazy val akkaVersion = "2.5.21"

resolvers += Resolver.jcenterRepo
resolvers += Resolver.bintrayRepo("ovotech", "maven")
libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-persistence" % akkaVersion,
  "com.typesafe.akka" % "akka-http_2.12" % "10.1.5",
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion,
  "org.scalatest" %% "scalatest" % "3.0.1" % "test",
  "com.typesafe.akka" %% "akka-stream" % akkaVersion,
  "org.scalatest" %% "scalatest" % "3.2.0-SNAP9",
  "joda-time" % "joda-time" % "2.7",
  "com.typesafe.akka" %% "akka-stream-kafka" % "0.22",
  "com.github.dnvriend" %% "akka-persistence-inmemory" % "2.5.1.1" % Test,
  "com.lightbend.akka" %% "akka-stream-alpakka-mongodb" % "1.0-M3",
  "org.mongodb" % "mongodb-driver-reactivestreams" % "1.11.0",
  "org.mongodb.scala" %% "mongo-scala-driver" % "2.6.0"
)

libraryDependencies += "com.typesafe.akka" %% "akka-slf4j" % akkaVersion
libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.2.3"

libraryDependencies += compilerPlugin(
  "org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full
)

libraryDependencies ++= {
  val kafkaSerializationV = "0.3.11" // see the Maven badge above for the latest version
  Seq(
    "com.ovoenergy" %% "kafka-serialization-core" % kafkaSerializationV //,
    //    "com.ovoenergy" %% "kafka-serialization-circe" % kafkaSerializationV, // To provide Circe JSON support
    //    "com.ovoenergy" %% "kafka-serialization-json4s" % kafkaSerializationV // To provide Json4s JSON support
  )
}

libraryDependencies ++= Seq(
  "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-core" % "0.34.1" % Compile,
  "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-macros" % "0.34.1" % Provided // required only in compile-time
)

unmanagedSourceDirectories in Compile += file(baseDirectory.value + "/src-ext/contrib/src/main/scala")
unmanagedSourceDirectories in Compile += file(baseDirectory.value + "/src-ext/contrib/src/main/java")
unmanagedResourceDirectories in Compile += file(baseDirectory.value + "/src-ext/contrib/src/main/resources")
unmanagedSourceDirectories in Test += file(baseDirectory.value + "/src-ext/contrib/src/test/scala")
unmanagedSourceDirectories in Test += file(baseDirectory.value + "/src-ext/contrib/src/test/java")
unmanagedResourceDirectories in Test += file(baseDirectory.value + "/src-ext/contrib/src/test/resources")

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion % "provided",
  "junit" % "junit" % "4.12" % Test, // Common Public License 1.0
  "com.novocode" % "junit-interface" % "0.11" % Test, // BSD-like
  "com.google.jimfs" % "jimfs" % "1.1" % Test, // ApacheV2
  "com.miguno.akka" % "akka-mock-scheduler_2.12" % "0.5.1" % Test
)
libraryDependencies += "com.github.pathikrit" %% "better-files" % "3.7.1"
addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.9")


//libraryDependencies += "com.typesafe.akka" %% "akka-stream-contrib" % "0.6"
val sampleTask = taskKey[Int]("sample")
sampleTask := {
  println(s"sbtVersion=${sbtVersion.value}")
  println(baseDirectory.value + "/src-ext/contrib/src")
  0
}