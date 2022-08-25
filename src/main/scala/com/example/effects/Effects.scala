package com.example.effects

import scala.concurrent.Future
import scala.io.StdIn.readLine

object Effects {

  // functional programming
  // EXPRESSIONS

  def combine(a: Int, b: Int): Int = a + b // mini functional program

  // local reasoning = type signature describes the kind of computation that will be performed.
  // referential transparency = ability to replace an expression with the value that it evaluates to
  val five: Int = combine(2, 3)
  val five_v2: Int = 2 + 3
  val five_v3 = 5

  // not all expressions are Referential transparency
  val resultOfPrinting: Unit = println("Learning ZIO")
  val resultOfPrinting_v2: Unit = () // not the same.

  // example 2: changing a variable
  var anInt = 0
  val changingInt: Unit = (anInt = 42) // side effect
  val changingInt_v2: Unit = () // not the same program

  // side effects are inevitable.

  // We want to define a data structure to bridge the side effect programs with the functional programing capabilities: local reasoning, referential transparency


  /* Effect desires:
        - the type signature describes what KIND of computation it will perform
        - the type signature describes the type of VALUE that it will produce
        - if side effects are required, construction must be separate from the EXECUTION
   */

  /*
      Example 1: Option = possible absent values
        - type signature describes the kind of computation = a possibly absent value
        - type signature says that the computation returns an A, if the computation does produce something.
        - no side effects are needed

        => option is an effect
   */

  val anOption: Option[Int] = Option(42)

  /*
      Example 2: Future
        - describes an async computation
        - produces a value of type A, if it finishes and it's successful
        - There are side effects when you build a future. You need an execution context, threads. Construction is not separate from the execution, creation of futures are eager
   */

  import scala.concurrent.ExecutionContext.Implicits.global

  val aFuture: Future[Int] = Future(42)

  /*
      Example 3: MyIO
        - describes a computation which might performs side effects
        - produces value of type A if the computation is successful
        - side effects are required, construction is separate from execution

        My IO is an effect!!!!
   */

  case class MyIO[A](unsafeRun: () => A) {
    def map[B](f: A => B): MyIO[B] = MyIO(() => f(unsafeRun()))

    def flatMap[B](f: A => MyIO[B]): MyIO[B] = MyIO(() => f(unsafeRun()).unsafeRun())
  }

  val anIoWithSideEffects: MyIO[Int] = MyIO[Int](() => {
    println("producing effect") // "production effect" will not be print in console until we invoke unsafeRun()
    42
  })

  /**
   * Exercises -
   * Create IO data types which:
   *     1. measure the current time of the system
   *        2. measure the duration of a computation
   *        3. reads something from the console
   *        4. print something to the console (e.g. what's your name) then read, then print a welcome message
   *
   */

  // 1

  val currentTimeIO: MyIO[Long] = MyIO[Long](() => System.currentTimeMillis())

  // 2
  def measure[A](computation: MyIO[A]): MyIO[(Long, A)] =
    for {
      startTime <- currentTimeIO
      value <- computation
      endTime <- currentTimeIO
    } yield (endTime - startTime, value)

  // equivalent impl using flatMap
  def measureV2[A](computation: MyIO[A]): MyIO[(Long, A)] = {
    currentTimeIO.flatMap(startTime =>
      computation.flatMap(value =>
        currentTimeIO.map(endTime =>
          (endTime - startTime, value)
        )
      )
    )
  }

  // 3
  val readLineIO: MyIO[String] = MyIO[String](() => readLine())

  // 4
  def putString(line: String): MyIO[Unit] = MyIO[Unit](() => println(line))

  val inputOutputIO: MyIO[Unit] = for {
    _ <- putString("What's your name?")
    name <- readLineIO
    _ <- putString(s"Hello, my name is: $name")
  } yield ()


  def demoComputation(): Unit = {
    val computation = MyIO(() => {
      println("Crunching numbers...")
      Thread.sleep(1000)
      println("Done!")
      42
    })

    println(measure(computation))
  }

  /**
   * A simplified ZIO effect
   * -> MyZIO has an error channel + resources identified by R
   *  -> MyZIO datatype because it's a producer of value of type A, it needs to be covariant on type A
   *  -> because MyZIO consumes R, it means the R is a contravariant
   *
   */
  case class MyZIO[-R, +E, +A](unsafeRun: (R) => Either[E, A]) {
    def map[B](f: A => B): MyZIO[R, E, B] =
      MyZIO(r => unsafeRun(r) match {
        case Left(e) => Left(e)
        case Right(v) => Right(f(v))
      })

    def flatMap[R1 <: R, E1 >: E, B](f: A => MyZIO[R1, E1, B]): MyZIO[R1, E1, B] =
      MyZIO(r => unsafeRun(r) match {
        case Left(e) => Left(e)
        case Right(v) => f(v).unsafeRun(r)
      })
  }


  def main(args: Array[String]): Unit = {
    anIoWithSideEffects.unsafeRun()
    inputOutputIO.unsafeRun()
    //    demoComputation()
  }

}
