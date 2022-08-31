package com.example.concurrency

import zio._
import com.example.utils._

object Interruptions extends ZIOAppDefault {

  val zioWithTime =
    ZIO.succeed("starting computation").debugThread *>
      ZIO.sleep(2.seconds) *>
      ZIO.succeed(42).debugThread

  // You can guard against interruptions
  val zioWithTime_v2 =
    (
      ZIO.succeed("starting computation").debugThread *>
        ZIO.sleep(2.seconds) *>
        ZIO.succeed(42).debugThread
      ).onInterrupt(ZIO.succeed("I was interrupted!").debugThread) /* The callback it's useful for cleaning up the resources */
  // other methods: onInterrupt, onDone

  val interruption = for {
    fib <- zioWithTime.fork
    // interrupt fib
    _ <- ZIO.sleep(1.seconds) *> ZIO.succeed("Interrupting!").debugThread *> fib.interrupt /* this is an effect */
    _ <- ZIO.succeed("Interruption successful").debugThread
    result <- fib.join
  } yield result

  val interruption_v2 = for {
    fib <- zioWithTime_v2.fork
    // interrupt fib
    _ <- ZIO.sleep(1.seconds) *> ZIO.succeed("Interrupting!").debugThread *> fib.interrupt /* this is an effect */
    _ <- ZIO.succeed("Interruption successful").debugThread
    result <- fib.join
  } yield result

  val interruption_v3 = for {
    fib <- zioWithTime_v2.fork
    // interrupt fib
    _ <- (ZIO.sleep(1.seconds) *> ZIO.succeed("Interrupting!").debugThread *> fib.interrupt).fork
    _ <- ZIO.succeed("Interruption successful").debugThread
    result <- fib.join
  } yield result

  // Interruption a fiber from another fiber it's build in the API and it's available using the method: interruptFork

  val interruption_v4 = for {
    fib <- zioWithTime_v2.fork
    // interrupt fib
    _ <- ZIO.sleep(1.seconds) *> ZIO.succeed("Interrupting!").debugThread *> fib.interruptFork
    _ <- ZIO.succeed("Interruption successful").debugThread
    result <- fib.join
  } yield result

  /*
     Cases when interruption happen automatically
      Fibers have parent child relationship
   */

  // Parent Effect
  val parentEffect =
    ZIO.succeed("spanning fiber").debugThread *>
      zioWithTime_v2.fork *> // child fiber
      ZIO.sleep(1.second) *>
      ZIO.succeed("Parent successful").debugThread // done here
  // zioWithTime fiber sleeps for 2 seconds, so will remain without parent.

  val testOutlivingParent = for {
    parentEffectFib <- parentEffect.fork
    _ <- ZIO.sleep(3.seconds)
    _ <- parentEffectFib.join
  } yield ()

  // Child fiber will be automatically interrupted, the child fiber

  // If you want to fiber to be the child of the mail application fibers, use: forkDaemon on the child fiber
  // Parent Effect
  val parentEffect_v2 =
  ZIO.succeed("spanning fiber").debugThread *>
    zioWithTime_v2.forkDaemon *> // child fiber
    ZIO.sleep(1.second) *>
    ZIO.succeed("Parent successful").debugThread // done here
  // zioWithTime fiber sleeps for 2 seconds, so will remain without parent.

  val testOutlivingParent_v2 = for {
    parentEffectFib <- parentEffect_v2.fork
    _ <- ZIO.sleep(3.seconds)
    _ <- parentEffectFib.join
  } yield ()

  // Racing -> feature of ZIOs which spam two fibers in parallel and the looser fiber will be interrupt once the other zio completed successfully.

  val slowEffect = (ZIO.sleep(2.seconds) *> ZIO.succeed("slow").debugThread).onInterrupt(ZIO.succeed("[Slow] interrupted").debugThread)
  val fastEffect = (ZIO.sleep(1.seconds) *> ZIO.succeed("fast").debugThread).onInterrupt(ZIO.succeed("[Fast] interrupted").debugThread)

  val aRace = slowEffect.race(fastEffect)

  val testRace = aRace.fork *> ZIO.sleep(3.seconds)

  /** Exercise
   */

  // 1 - implement a timeout function
  def timeout[R, E, A](zio: ZIO[R, E, A], time: Duration): ZIO[R, E, A] = {
    for {
      fib <- zio.fork
      _ <- (ZIO.sleep(time) *> fib.interrupt).fork
      result <- fib.join
    } yield result
  }

  val testTimeout = timeout(
    ZIO.succeed("Starting....").debugThread *> ZIO.sleep(2.seconds) *> ZIO.succeed("I made it!").debugThread,
    1.seconds
  ).debugThread

  // 2 - timeout v2
  def timeout_v2[R, E, A](zio: ZIO[R, E, A], time: Duration): ZIO[R, E, Option[A]] =
    timeout(zio, time).foldCauseZIO(
      cause => if (cause.isInterrupted) ZIO.succeed(None) else ZIO.failCause(cause),
      value => ZIO.succeed(Some(value))
    )

  val testTimeout_v2 = timeout_v2(
    ZIO.succeed("Starting....").debugThread *> ZIO.sleep(2.seconds) *> ZIO.succeed("I made it!").debugThread,
    1.seconds
  ).debugThread


  override def run = testTimeout_v2

}
