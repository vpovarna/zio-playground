package com.example.testing

import zio._
import zio.test._

object SimpleDependencySpec extends ZIOSpecDefault {

  def spec = test("simple dependency") {
    val aZIO: ZIO[Int, Nothing, Int] = ZIO.succeed(42)
    assertZIO(aZIO)(Assertion.equalTo(42))
  }.provide(ZLayer.succeed(19))
}

// Example: User Survey application
object BusinessLogicSpec extends ZIOSpecDefault {

  // dependency
  abstract class Database[K, V] {
    def get(key: K): Task[K]

    def put(key: K, value: V): Task[Unit]
  }

  object Database {
    def create(url: String): UIO[Database[String, String]] = ??? // the real thing
  }

  // business logic under test
  def normalizeUsername(name: String): UIO[String] = ZIO.succeed(name.toUpperCase())

  val mockerDatabase = ZIO.succeed(new Database[String, String] {

    import scala.collection.mutable

    val map = mutable.Map[String, String]()

    override def get(key: String): Task[String] = ZIO.attempt(map(key))

    override def put(key: String, value: String): Task[Unit] = ZIO.succeed(map += (key -> value))
  })

  // Test
  override def spec: Spec[TestEnvironment with Scope, Any] = suite("A user survey application should...") {
    test("normalize user names") {
      val surveyPreliminaryLogic = for {
        db <- ZIO.service[Database[String, String]]
        _ <- db.put("123", "Daniel")
        username <- db.get("123")
        normalized <- normalizeUsername(username)
      } yield normalized
      assertZIO(surveyPreliminaryLogic)(Assertion.equalTo("DANIEL"))
    }
  }.provide(ZLayer.fromZIO(mockerDatabase)) // the provide can be added to the test suite or to the test case.

}

/*
   Build-in test services
 */

object DummyConsoleApplication {
  def welcomeUser(): Task[Unit] = for {
    _ <- Console.printLine("Please enter your name...")
    name <- Console.readLine("")
    _ <- Console.printLine(s"Welcome, $name!")
  } yield ()
}

object BuildTestServiceSpec extends ZIOSpecDefault {
  override def spec = suite("Checking build-in test servcies")(
    test("ZIO console application") {
      val logicUnderTest: Task[Vector[String]] = for {
        _ <- TestConsole.feedLines("Daniel")
        _ <- DummyConsoleApplication.welcomeUser()
        output <- TestConsole.output
      } yield output.map(_.trim)

      assertZIO(logicUnderTest)(Assertion.hasSameElements(List("Please enter your name...", "", "Welcome, Daniel!")))
    },

    test ("ZIO clock") {
      val parallelEffect = for {
        fiber <- ZIO.sleep(5.minutes).timeout(1.minute).fork
        _ <- TestClock.adjust(1.minute)
        result <- fiber.join
      } yield result

      assertZIO(parallelEffect)(Assertion.isNone)
    },

    test ("test random") {
      // TestRandom will feed the values that you need.
      val effect = for {
        _ <- TestRandom.feedInts(3,4,1,2)
        value <- Random.nextInt
      } yield value

      assertZIO(effect)(Assertion.equalTo(3))
    }
  )
}
