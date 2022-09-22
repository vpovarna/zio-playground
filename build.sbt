name := "zio-playground"
version := "0.1"
scalaVersion := "2.13.8"

lazy val zioVersion = "2.0.0"
lazy val zioKafka = "2.0.0"
lazy val zioJson = "0.3.0"

libraryDependencies ++= Seq(
  "dev.zio" %% "zio" % zioVersion,
  "dev.zio" %% "zio-test" % zioVersion,
  "dev.zio" %% "zio-test-sbt" % zioVersion,
  "dev.zio" %% "zio-streams" % zioVersion,
  "dev.zio" %% "zio-test-junit" % zioVersion,
  "dev.zio" %% "zio-kafka" % zioKafka,
  "dev.zio" %% "zio-json" % zioJson
)

testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
