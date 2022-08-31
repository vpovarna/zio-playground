package com.example.concurrency

import zio._
import com.example.utils._

object MasteringInterruptions extends ZIOAppDefault {

  // Interruptions:
  // fib.interrupt
  // ZIO.race, ZIO.zipPar, ZIO.collectAllPar
  // outlive parent fiber


  // manual interruption
  val aManuallyInterruptedZIO =
    ZIO.succeed("computing...").debugThread *> ZIO.interrupt *> ZIO.succeed(42).debugThread // the effect will not print the 42 value

  // finalizer
  val effectWithInterruptionFinalizer = aManuallyInterruptedZIO.onInterrupt(ZIO.succeed("I was interrupted").debugThread)

  // uninterruptible
  // payment flow to NOT be interrupted
  val fussyPaymentSystem = (
    ZIO.succeed("Payment running, don't cancel me...").debugThread *>
      ZIO.sleep(1.second) *> // simulate the actual payment
      ZIO.succeed("Payment completed").debugThread
    ).onInterrupt(ZIO.succeed("MEGA CANCEL OF DOOM!").debugThread) // don't want this triggered

  val cancellationOfDoom = for {
    fib <- fussyPaymentSystem.fork
    _ <- ZIO.sleep(500.millis) *> fib.interrupt
    _ <- fib.join
  } yield () // This will interrupt the fussyPaymentSystem after 1/2 seconds.

  // To fix this ^, there is a concept of ZIO.uninterruptible

  val atomicPayment_v1 = ZIO.uninterruptible(fussyPaymentSystem) // this will make the ZIO chain atomic.
  val atomicPayment_v2 = fussyPaymentSystem.uninterruptible // this will make the ZIO chain atomic. same result as the previous effect

  val noCancellationOfDoom = for {
    fib <- atomicPayment_v1.fork
    _ <- ZIO.sleep(500.millis) *> fib.interrupt
    _ <- fib.join
  } yield ()

  // interruptibility is a regional setting
  val zio1 = ZIO.succeed(1)
  val zio2 = ZIO.succeed(2)
  val zio3 = ZIO.succeed(3)
  val zioComposed = (zio1 *> zio2 *> zio3).uninterruptible // ALL the ZIOs are uninterruptible
  val zioComposed2 = (zio1 *> zio2.interruptible *> zio3).uninterruptible // inner scope override outer scope; zio2 remains interruptible

  // uninterruptibleMask -> allows you to define which parts should be interruptible and which parts should be not
  // example: an authentication service

  // Part 1: enter your password; if it take to long it will send you back to verification page
  // Part 2: Once the password is submitted the verification should not be interrupted.

  val inputPassword = for {
    _ <- ZIO.succeed("Input password").debugThread
    _ <- ZIO.succeed("(typing the password)").debugThread
    _ <- ZIO.sleep(5.seconds)
    pass <- ZIO.succeed("RockTheJVM1!")
  } yield pass

  def verificationPassword(pass: String) = for {
    _ <- ZIO.succeed("verifying...").debugThread
    _ <- ZIO.sleep(2.seconds)
    result <- ZIO.succeed(pass == "RockTheJVM1!")
  } yield result

  val authFlow = ZIO.uninterruptibleMask { restore =>
    // everything is uninterruptible except all the effects wrapped in restore
    for {
      pw <- restore(inputPassword).onInterrupt(ZIO.succeed("Authentication time out. Try again later.").debugThread)
      verification <- verificationPassword(pw)
      _ <- if (verification) ZIO.succeed("Authentication successful.").debugThread
      else ZIO.succeed("Authentication failed").debugThread
    } yield ()
  }

  val authProgram = for {
    authFib <- authFlow.fork
    _ <- ZIO.sleep(3.seconds) *> ZIO.succeed("Attempting to cancel authentication").debugThread *> authFib.interrupt
    _ <- authFib.join
  } yield ()

  /**
   *
   *  Exercises
   */

    val cancelBeforeMol = ZIO.interrupt *> ZIO.succeed(42).debugThread // 42 will not be printed. ZIO.interrupt is an interruption trigger. Te effect is cancellable because of the ZIO.interrupt
    val uncancellBeforeMol = ZIO.uninterruptible(ZIO.interrupt *> ZIO.succeed(42).debugThread)  // 42 will not be printed beacuse of the ZIO.interrupt effect whcih is evaluated first.

  override def run = cancelBeforeMol
}
