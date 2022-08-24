package com.example.effects

import zio._

import scala.io.StdIn

object ZIOEffects {

  // zio._ -> will contains everything that we need

  // meaningOfLife ZIO effect will not require effects, this is why the resource is ANY, it doesn't failed, this si why it's nothing and value channel is INT

  val meaningOfLife: ZIO[Any, Nothing, Int] = ZIO.succeed(42)
  val aFailure: ZIO[Any, String, Nothing] = ZIO.fail("Something is wrong!")
  val aSuspendZIO: ZIO[Any, Throwable, Int] = ZIO.suspend(meaningOfLife)

  // map + flatMap

  val improvedMeaningOfLife: ZIO[Any, Nothing, Int] = meaningOfLife.map(_ * 2)
  val printingMeaningOfLife: ZIO[Any, Nothing, Unit] = meaningOfLife.flatMap(mol => ZIO.succeed(println(mol)))

  val program: ZIO[Any, Nothing, Unit] = for {
    _ <- ZIO.succeed(println("What's your name"))
    name <- ZIO.succeed(StdIn.readLine())
    _ <- ZIO.succeed(println(s"Welcome to ZIO $name"))
  } yield ()

  // !!! All the ZIOs are constructed but not evaluated.

  // ZIO combinator
  val anotherMOL: ZIO[Any, Nothing, Int] = ZIO.succeed(100)
  val tupledZIO: ZIO[Any, Nothing, (Int, Int)] = meaningOfLife.zip(anotherMOL)

  // Zip With will add a combination function
  val combinedZIO: ZIO[Any, Nothing, Int] = meaningOfLife.zipWith(anotherMOL)(_ + _)

  /**
   * Type alias of ZIOs
   *
   */

  // UIO = ZIO[Any, Nothing, A] -> no requirements, cannot failed, produces A
  val aUID: UIO[Int] = ZIO.succeed(99)

  // URIO[R,A] = ZIO[R, Nothing, A] -> has requirements, cannot failed, produces A
  val aURIO: URIO[Int, Int] = ZIO.succeed(44)

  // RIO[R,A] = ZIO[R, Throwable, A] - can fail with a Throwable
  val anRIO: RIO[Int, Int] = ZIO.succeed(99)
  val aFailedRIO: RIO[Int, Int] = ZIO.fail(new RuntimeException("Rio failed"))

  // Task[A] = ZIO[Any, Throwable, A] -> no requirements, can failed with Throwable, produces A
  val aSuccessfulTask: Task[Int] = ZIO.succeed(89)
  val aFailedTask: Task[Int] = ZIO.fail(new RuntimeException("Something bad"))

  // IO[E,A] = ZIO[Any, E,A] -> no requirements
  val aSuccessfulIO: IO[String, Int] = ZIO.succeed(34)
  val aFailureIO: IO[String, Int] = ZIO.fail("Something bad")

  /*
        Exercises:
   */

  // 1. sequence two ZIOs and take the value of the last one. Evaluate zioa and than ziob and take the value of ziob
  def sequenceTaskLastV1[R, E, A, B](zioa: ZIO[R, E, A], ziob: ZIO[R, E, B]): ZIO[R, E, B] =
    zioa.flatMap(_ => ziob.map(b => b))

  def sequenceTaskLastV2[R, E, A, B](zioa: ZIO[R, E, A], ziob: ZIO[R, E, B]): ZIO[R, E, B] =
    for {
      _ <- zioa
      b <- ziob
    } yield b

  // IN ZIO Library
  def sequenceTaskLastV3[R, E, A, B](zioa: ZIO[R, E, A], ziob: ZIO[R, E, B]): ZIO[R, E, B] = zioa *> ziob

  // 2. sequence two ZIOs and take the value of the fist one. Evaluate zioa and than ziob and take the value of zioa
  def sequenceTaskFirstV1[R, E, A, B](zioa: ZIO[R, E, A], ziob: ZIO[R, E, B]): ZIO[R, E, A] =
    for {
      a <- zioa
      _ <- ziob
    } yield a

  def sequenceTaskFirstV2[R, E, A, B](zioa: ZIO[R, E, A], ziob: ZIO[R, E, B]): ZIO[R, E, A] =
    zioa.flatMap(a => ziob.map(_ => a))

  // IN ZIO Library
  def sequenceTaskFirstV3[R, E, A, B](zioa: ZIO[R, E, A], ziob: ZIO[R, E, B]): ZIO[R, E, A] = zioa <* zioa

  // 3. run a zio forever
  def runForever[R, E, A](zio: ZIO[R, E, A]): ZIO[R, E, A] =
    zio.flatMap(_ => runForever(zio))

  def runForever_v2[R, E, A](zio: ZIO[R, E, A]): ZIO[R, E, A] =
    zio *> runForever_v2(zio)

  val endlessLoop: ZIO[Any, Nothing, Unit] = runForever {
    ZIO.succeed {
      println("running")
      Thread.sleep(1000)
    }
  }

  // 4. convert the value of a ZIO to something else
  def convert_v1[R, E, A, B](zio: ZIO[R, E, A], b: B): ZIO[R, E, B] = for {
    _ <- zio
  } yield b

  def convert_v2[R, E, A, B](zio: ZIO[R, E, A], b: B): ZIO[R, E, B] = zio.map(_ => b)

  // ZIO build in method
  def convert_v3[R, E, A, B](zio: ZIO[R, E, A], b: B): ZIO[R, E, B] = zio.as(b)


  // 5. discard the value of a ZIO to unit
  def asUnit[R, E, A](zio: ZIO[R, E, A]): ZIO[R, E, Unit] = for {
    _ <- zio
  } yield ()

  def asUnit_v2[R, E, A](zio: ZIO[R, E, A]): ZIO[R, E, Unit] = zio.map(_ => ())

  def asUnit_v3[R, E, A](zio: ZIO[R, E, A]): ZIO[R, E, Unit] = asUnit(zio)

  // 6 - recursion
  def sum(n: Int): Int =
    if (n == 0) 0
    else n + sum(n - 1) // will crash at sum(20000)

  def sumZIO(n: Int): UIO[Int] = {
    if (n == 0) ZIO.succeed(0)
    else for {
      current <- ZIO.succeed(n)
      previousSum <- sumZIO(n - 1)
    } yield (current + previousSum)
  }

  // 7 - fibonacci

  def fibo(n: Int): BigInt = {
    if (n <= 2) 1
    else fibo(n - 1) + fibo(n - 2)
  }

  // The problem with this approach is that we are uisng two recurision, the last one depends on the value of the fist one.
  def fiboZIO(n: Int): UIO[BigInt] = {
    if (n <= 2) ZIO.succeed(1)
    else for {
      last <- fiboZIO(n - 1)
      prev <- fiboZIO(n - 2)
    } yield last + prev
  }

  // The for comprehensive is
  //  fiboZIO(n-1).flatMap(last => fiboZIO(n-2).map(prev => last + prev))

  // The solution is to use ZIO.
  def fiboZIOWithSuspend(n: Int): UIO[BigInt] = {
    if (n <= 2) ZIO.succeed(1)
    else for {
      last <- ZIO.suspendSucceed(fiboZIO(n - 1))
      prev <- fiboZIO(n - 2)
    } yield last + prev
  }

  def main(args: Array[String]): Unit = {
    // Evaluate the ZIO in main manually using Unsafe API
    val runtime = Runtime.default
    implicit val trace: Trace = Trace.empty // functionality which allow you to debug your code regardless if it's in main thread or on another thread.
    Unsafe.unsafe { implicit u =>
      //      val mol = runtime.unsafe.run(meaningOfLife)
      //      println(mol)
      val firstEffect = ZIO.succeed {
        println("computing first effect...")
        Thread.sleep(1000)
        1
      }
      val secondEffect = ZIO.succeed {
        println("computing second effect...")
        Thread.sleep(1000)
        2
      }
      // val result = runtime.unsafe.run(sequenceTaskLastV1(firstEffect, secondEffect))

      // endless loop
      // runtime.unsafe.run(runForever(endlessLoop))
      val result = runtime.unsafe.run(sumZIO(20001))
      println(result)
    }
  }

}
