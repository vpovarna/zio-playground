package com.example.concurrency

import zio._

object ZioFibersPlayground extends ZIOAppDefault {

  val showerTime: UIO[String] = ZIO.succeed("Taking shower")
  val boilingWater: UIO[String] = ZIO.succeed("Boiling Water")
  val preparingCoffee: UIO[String] = ZIO.succeed("Prepare some coffee")

  def printThread = s"[${Thread.currentThread().getName}]"

  def syncProgram(): UIO[Unit] = for {
    _ <- showerTime.debug(printThread)
    _ <- boilingWater.debug(printThread)
    _ <- preparingCoffee.debug(printThread)
  } yield ()

  // fiber = schedulable computation which is not mapped to specific thread. ZIO runtime will be scheduled on specific thread.

  // Signature: Fiber[E, A]

  def concurrentBathroomWhileBoilingWater() = for {
    _ <- showerTime.debug(printThread).fork
    _ <- boilingWater.debug(printThread)
    _ <- preparingCoffee.debug(printThread)
  } yield ()

  def concurrentRouting() = for {
    fibShowerTime <- showerTime.debug(printThread).fork
    fibBoilingWater <- boilingWater.debug(printThread).fork
    zippedFib = fibShowerTime.zip(fibBoilingWater)
    result <- zippedFib.join.debug(printThread) // block the current fiber until the effect is completed
    _ <- ZIO.succeed(s"$result done").debug(printThread) *> preparingCoffee.debug(printThread)
  } yield () // fork join in pure functional programming

  // Interruptions

  val callFromAlice: UIO[String] = ZIO.succeed("Call from Alice")
  val boilingWatterWithTime = boilingWater.debug(printThread) *> ZIO.sleep(5.seconds) *> ZIO.succeed("Boiled water ready")

  def concurrentRoutingWithAliceCall() = for {
    _ <- showerTime.debug(printThread)
    boilingWaterFib <- boilingWatterWithTime.fork
    _ <- callFromAlice.debug(printThread).fork *> boilingWaterFib.interrupt.debug(printThread)
    _ <- ZIO.succeed("Screw my coffee, going with Alice").debug(printThread)
  } yield ()

  // interrupting a fiber is not an expensive operations

  // how do you make the execution uninteruptable
  val prepareCoffeeWithTime = preparingCoffee.debug(printThread) *> ZIO.sleep(5.seconds) *> ZIO.succeed("Coffee reddy").debug(printThread)

  def concurrentRoutingWithShowerArHome() = for {
    _ <- showerTime.debug(printThread)
    _ <- boilingWater.debug(printThread)
    coffeeFib <- prepareCoffeeWithTime.fork.uninterruptible
    result <- callFromAlice.debug(printThread) *> coffeeFib.interrupt.debug(printThread)
    _ <- result match {
      case Exit.Success(_) => ZIO.succeed("Sorry Alice making breakfast at home!").debug(printThread)
      case _ => ZIO.succeed("Going to a coffee with Alice").debug(printThread)
    }
  } yield ()


  override def run = concurrentRoutingWithShowerArHome().exitCode
}
