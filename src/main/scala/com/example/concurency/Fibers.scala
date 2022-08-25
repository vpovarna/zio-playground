package com.example.concurency

import zio._
import com.example.utils._

import java.io.{File, FileWriter}
import scala.io.Source

object Fibers extends ZIOAppDefault {

  val meaningOfLife: ZIO[Any, Nothing, Int] = ZIO.succeed(42)
  val favoriteLanguage: ZIO[Any, Nothing, String] = ZIO.succeed("Scala")

  // Fiber = lightweight thread
  // Fiber is a description of a computation which runs on one of the threads managed by the ZIO runtime

  def createFiber: Fiber[Throwable, String] = ??? // impossible to create manually

  // Fibers are created through ZIO api create and schedule them automatically on the ZIO runtime

  // This is running on the same thread IO. Same execution.
  def combinator = for {
    mol <- meaningOfLife.debugThread
    lang <- favoriteLanguage.debugThread
  } yield (mol, lang)

  def differentThreadIOs = for {
    _ <- meaningOfLife.debugThread.fork
    _ <- favoriteLanguage.debugThread.fork
  } yield ()

  def meaningOfLifeFiber: ZIO[Any, Nothing, Fiber[Throwable, Int]] = meaningOfLife.fork

  // join a fiber
  def runOnAnotherThread[R, E, A](zio: ZIO[R, E, A]) = for {
    fib <- zio.fork
    result <- fib.join //  waits for the effect to complete
  } yield result


  // awaiting a fiber
  def runOnAnotherThread_v2[R, E, A](zio: ZIO[R, E, A]) = for {
    fib <- zio.fork
    result <- fib.await // await for the fiber to finish and returns the completion status
  } yield result match {
    case Exit.Success(value) => s"succeeded with $value"
    case Exit.Failure(cause) => s"failed with $cause"
  }


  // poll -> peek the result of a fiber RIGHT NOW without blocking. Will return an Option of am Exit
  val peekFiber: ZIO[Any, Nothing, Option[Exit[Throwable, RuntimeFlags]]] = for {
    fib <- ZIO.attempt {
      Thread.sleep(1000)
      42
    }.fork
    result <- fib.poll
  } yield result

  // Compose fibers - zip
  val zippedFibers = for {
    fib1 <- ZIO.succeed("Result from fiber 1").debugThread.fork
    fib2 <- ZIO.succeed("Result from fiber 2").debugThread.fork
    fib = fib1.zip(fib2)
    tuple <- fib.join // waits fot both effects to complete
  } yield tuple

  // Compose fibers - orElse
  val chainedFibers = for {
    fib1 <- ZIO.fail("Not Good!").debugThread.fork
    fib2 <- ZIO.succeed("Rock the JVM!").debugThread.fork
    fiber = fib1.orElse(fib2)
    message <- fiber.join
  } yield (message)

  /**
   * Exercise
   *
   */

  // 1. ZIP two fibers using fork and join
  // create a fiber that waits for both of this fibers
  def zipFiber[E, A, B](fiber1: Fiber[E, A], fiber2: Fiber[E, B]): ZIO[Any, Nothing, Fiber[E, (A, B)]] = {
    // Effect when evaluated will wait for fiber1 and fiber2
    val waitFib1: IO[E, A] = fiber1.join
    val waitFib2: IO[E, B] = fiber2.join
    val tupleZio = for {
      a <- waitFib1
      b <- waitFib2
    } yield (a, b)

    tupleZio.debugThread.fork
  }

  def zipFiber_v2[E, E1 <: E, E2 <: E, A, B](fiber1: Fiber[E1, A], fiber2: Fiber[E2, B]): ZIO[Any, Nothing, Fiber[E, (A, B)]] = {
    // Same impl
    val waitFib1: IO[E, A] = fiber1.join
    val waitFib2: IO[E, B] = fiber2.join
    val tupleZio = for {
      a <- waitFib1
      b <- waitFib2
    } yield (a, b)

    tupleZio.debugThread.fork
  }


  // 2. - Same thing with orElse
  def chainFibers[E, A](fiber1: Fiber[E, A], fiber2: Fiber[E, A]): ZIO[Any, Nothing, Fiber[E, A]] = {
    val waitFib1: IO[E, A] = fiber1.join
    val waitFib2: IO[E, A] = fiber2.join
    waitFib1.orElse(waitFib2).debugThread.fork
  }


  // 3 - distributing tasks between many fibers
  def generateRandomFile(path: String): Unit = {
    val random = scala.util.Random
    val chars = 'a' to 'z'

    val nWords = random.nextInt(2000)
    val content = (1 to nWords)
      .map(_ => (1 to random.nextInt(10)).map(_ => chars(random.nextInt(26))).mkString) //one word for every 1 to nWords
      .mkString(" ")

    val writer = new FileWriter(new File(path))
    writer.write(content)
    writer.flush()
    writer.close()
  }

  // An effect which read a file and count all words
  def countWords(path: String): Int = {
    val source = Source.fromFile(path)
    val wordCount = source
      .getLines()
      .mkString(" ")
      .split(" ")
      .count(_.nonEmpty)
    source.close()
    wordCount
  }

  def zioFileReader(path: String): ZIO[Any, Nothing, RuntimeFlags] = {
    ZIO.succeed(countWords(path))
  }

  // Spin up fibers to all files
  def wordCountParallel(n: Int): ZIO[Any, Nothing, Int] = {
    val effects: Seq[ZIO[Any, Nothing, Int]] = (1 to n).map(i => s"src/main/resources/testFile_$i.txt")
      .map(path => zioFileReader(path))
      .map(_.fork)
      .map((fiberEffect: ZIO[Any, Nothing, Fiber[Nothing, Int]]) => fiberEffect.flatMap(_.join)) // List of effects returning values

    effects.reduce { (zioA, zioB) =>
      for {
        a <- zioA
        b <- zioB
      } yield (a + b)
    }
  }

  // Spawn n fibers for counting the number of words in each file and then aggregate all the results together in one big number

  val zippedFibers_v2 = for {
    fib1 <- ZIO.succeed("result from fib1").debugThread.fork
    fib2 <- ZIO.succeed("result from fib2").debugThread.fork
    fib <- zipFiber(fib1, fib2)
    tuple <- fib.join
  } yield tuple

  override def run: ZIO[Any with ZIOAppArgs with Scope, Any, Any] = {
    //    runOnAnotherThread(meaningOfLife).debugThread
    //    runOnAnotherThread_v2(meaningOfLife).debugThread
    //    peekFiber.debugThread
    //    zippedFibers.debugThread

    //    chainedFibers.debugThread
    zippedFibers_v2

    // Files generator
    // ZIO.succeed((1 to 10).foreach(i => generateRandomFile(s"src/main/resources/testFile_$i.txt")))

    // Count words
    // wordCountParallel(10).debugThread

  }


}
