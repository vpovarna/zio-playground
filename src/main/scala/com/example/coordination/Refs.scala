package com.example.coordination

import zio._
import com.example.utils._

import java.time.Instant
import java.util.concurrent.TimeUnit

object Refs extends ZIOAppDefault {

  //  refs are functional atomic reference which protects read & writes.
  val atomicMOL: ZIO[Any, Nothing, Ref[Int]] = Ref.make(42)

  // obtain a value
  val mol = atomicMOL.flatMap { ref =>
    ref.get // returns a UIO[Int], thread-safe
  }

  // changing
  val setMol = atomicMOL.flatMap(_.set(100))

  // get + set in ONE Atomic operation
  val gsMol = atomicMOL.flatMap(_.getAndSet(500)) // returns the old value

  // update - run a function on the value
  val updatedMol: UIO[Unit] = atomicMOL.flatMap(_.update(_ * 100))

  // return the old value and the new value
  val updatedMolWithValue: ZIO[Any, Nothing, Int] = atomicMOL.flatMap { ref =>
    ref.updateAndGet(_ * 100) // returns the NEW value
    ref.getAndUpdate(_ * 100) // returns the OLD value
  }

  val modifyMOL: ZIO[Any, Nothing, String] = atomicMOL.flatMap { ref =>
    ref.modify(value => (s"my current value is $value", value * 100)) // the new value will be the INT, the return value will be a function over Int returning a String in this case
  }

  // Use case: Current and thread-safe over shared values

  // Demo: Word Counting

  def demoConcurrentWorkImpure(): UIO[Unit] = {
    var count = 0

    def task(workload: String): UIO[Unit] = {
      val wordCount = workload.split(" ").length
      for {
        _ <- ZIO.succeed(s"Counting words for: $wordCount").debugThread
        newCount <- ZIO.succeed(count + wordCount)
        _ <- ZIO.succeed(s"new total: $newCount").debugThread
        _ <- ZIO.succeed(count += count) // updating the variable
      } yield ()
    }

    def effects = List("I live ZIO", "This ref thing is cool", "Danile writes a lot if code").map(task)

    ZIO.collectAllParDiscard(effects)
  }

  /**
   * Not thread safe
   * mixing pure and impure code
   * hard to debug
   */

  def demoConcurrentWorkPure(): UIO[Unit] = {
    def task(workload: String, total: Ref[Int]): UIO[Unit] = {
      val wordCount = workload.split(" ").length
      for {
        _ <- ZIO.succeed(s"Counting words for: $wordCount").debugThread
        newCount <- total.updateAndGet(_ + wordCount)
        _ <- ZIO.succeed(s"new total: $newCount").debugThread
      } yield ()
    }

    for {
      counter <- Ref.make(0)
      _ <- ZIO.collectAllParDiscard(
        List("I live ZIO", "This ref thing is cool", "Danile writes a lot if code").map(load => task(load, counter))
      )
    } yield ()
  }

  /**
   * Exercises
   *
   */

  //1. Refactor the code using the Ref

  def tickingClockImpure() = {
    var ticks = 0L

    // print the current time every 1s + increase a counter ("ticks")
    def tickingCLock: UIO[Unit] = for {
      _ <- ZIO.sleep(1.second)
      _ <- Clock.currentTime(TimeUnit.MINUTES).debugThread
      _ <- ZIO.succeed(ticks += 1)
    } yield ()
    // print the total ticks every 5s


    def printTicks: UIO[Unit] = for {
      _ <- ZIO.sleep(5.seconds)
      _ <- ZIO.succeed(s"Ticks; $ticks").debugThread
      _ <- printTicks
    } yield ()

    tickingCLock.zipPar(printTicks).unit
  }

  // Code refactor

  def tickingClockPure(): ZIO[Any, Nothing, Unit] = {
    // print the current time every 1s + increase a counter ("ticks")
    def tickingCLock(ticks: Ref[Long]): UIO[Unit] = for {
      _ <- ZIO.sleep(1.second)
      _ <- Clock.currentTime(TimeUnit.MILLISECONDS).debugThread
      _ <- ticks.update(_ + 1)
      _ <- tickingCLock(ticks)
    } yield ()
    // print the total ticks every 5s

    def printTicks(ticks: Ref[Long]): UIO[Unit] = for {
      _ <- ZIO.sleep(5.seconds)
      tick <- ticks.get
      _ <- ZIO.succeed(s"Ticks: $tick").debugThread
      _ <- printTicks(ticks)
    } yield ()

    for {
      tick <- Ref.make(0L)
      _ <- tickingCLock(tick).zipPar(printTicks(tick))
    } yield ()
  }

  // 2.

  def tickingClockPure_v2(): ZIO[Any, Nothing, Unit] = {
    val ticksRef = Ref.make(0L) // the value will not be incremented.

    // print the current time every 1s + increase a counter ("ticks")
    def tickingCLock: UIO[Unit] = for {
      ticks <- ticksRef
      _ <- ZIO.sleep(1.second)
      _ <- Clock.currentTime(TimeUnit.MILLISECONDS).debugThread
      _ <- ticks.update(_ + 1)
      _ <- tickingCLock
    } yield ()
    // print the total ticks every 5s

    def printTicks: UIO[Unit] = for {
      ticks <- ticksRef
      _ <- ZIO.sleep(5.seconds)
      tick <- ticks.get
      _ <- ZIO.succeed(s"Ticks: $tick").debugThread
      _ <- printTicks
    } yield ()

    tickingCLock.zipPar(printTicks)
  }

  // update function can be run more than 1

  def demoMultipleUpdates() = {
    def task(id: Int, ref: Ref[Int]): UIO[Unit] = ref.modify(prev => (println(s"Task $id updating ref at $prev"), id))

    for {
      ref <- Ref.make(0)
      _ <- ZIO.collectAllParDiscard((1 to 10).toList.map(i => task(i, ref)))
    } yield ()
  }

  override def run = demoMultipleUpdates()
}
