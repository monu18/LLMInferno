// Enable the sbt-protoc plugin
//enablePlugins(com.thesamet.sbtprotoc.ProtocPlugin)

// Project Metadata
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "2.12.20"

// Project Definition
lazy val root = (project in file("."))
  .settings(
    name := "LLMInferno",
    Compile / PB.targets := Seq(
      scalapb.gen(grpc = true) -> (Compile / sourceManaged).value / "scalapb"
    ),
    Compile / PB.includePaths := Seq((Compile / sourceDirectory).value / "protobuf"), // Specify custom protobuf path
    Compile / mainClass := Some("HW3"),
//    Compile / unmanagedSourceDirectories += baseDirectory.value / "src" / "main"
  )

// Version Definitions
val logbackVersion = "1.5.6"
val slf4jLoggerVersion = "2.0.12"
val typeSafeConfigVersion = "1.4.3"
val ollamaVersion = "1.0.79"
val grpcVersion = "1.64.0"
val sparkVersion = "3.4.1"
val awsSdkVersion = "2.26.25"
val grpcNettyVersion = "1.65.1"
val grpcProtobufVersion = "1.65.1"
val scalapbVersion = "0.11.17"

// Dependencies
libraryDependencies ++= Seq(
  // Logging and Configuration
  "ch.qos.logback" % "logback-classic" % logbackVersion,
  "org.slf4j" % "slf4j-api" % slf4jLoggerVersion,
  "com.typesafe" % "config" % typeSafeConfigVersion,

  // Ollama API
  "io.github.ollama4j" % "ollama4j" % ollamaVersion,

  // gRPC Dependencies
  "io.grpc" % "grpc-netty" % grpcNettyVersion,      // Transport layer
  "io.grpc" % "grpc-protobuf" % grpcProtobufVersion, // Protocol Buffers for gRPC
  "io.grpc" % "grpc-stub" % grpcVersion,           // Stub layer

  // Apache Spark for distributed computation
  "org.apache.spark" %% "spark-core" % sparkVersion,
  "org.apache.spark" %% "spark-sql" % sparkVersion,
  "org.apache.spark" %% "spark-mllib" % sparkVersion,

  // AWS SDK
  "software.amazon.awssdk" % "core" % awsSdkVersion,
  "software.amazon.awssdk" % "s3" % awsSdkVersion,
  "software.amazon.awssdk" % "bedrock" % awsSdkVersion, // For Bedrock-specific operations

  // Protobuf Java runtime
  "com.google.protobuf" % "protobuf-java" % "4.27.1",
  // ScalaPB runtime
  "com.thesamet.scalapb" %% "scalapb-runtime" % scalapbVersion,
  // ScalaPB gRPC runtime
  "com.thesamet.scalapb" %% "scalapb-runtime-grpc" % scalapbVersion,
)

// Assembly Plugin Settings
enablePlugins(AssemblyPlugin)

assembly / assemblyJarName := "LLMInferno.jar"
assembly / mainClass := Some("HW3")

