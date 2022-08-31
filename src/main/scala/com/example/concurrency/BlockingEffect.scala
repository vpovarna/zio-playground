package com.example.concurrency

import zio._
import com.example.utils._

import java.util.concurrent.atomic.AtomicBoolean

object BlockingEffect extends ZIOAppDefault {

  def blockingTask(n: Int): ZIO[Any, Nothing, Unit] =
    ZIO.succeed(s"running blocking task $n").debugThread *>
      ZIO.succeed(Thread.sleep(3000)) *>
      blockingTask(n)

  val program = ZIO.foreachPar((1 to 100).toList)(blockingTask)
  // thread starvation

  // If you know if there are blocking task you should delegate them to the blocking thread pool
  val aBlockingIO = ZIO.attemptBlocking {
    println(s"[${Thread.currentThread().getName}] running a long computation....")
    Thread.sleep(10000)
    42
  }

  // blocking code cannot be usually interrupted.

  val tryInterrupting = for {
    blockingFib <- aBlockingIO.fork
    _ <- ZIO.sleep(1.second) *> ZIO.succeed("interrupting...").debugThread *> blockingFib.interrupt
    mol <- blockingFib.join
  } yield mol // the effect is not actually interrupted.

  // to actually interrupt use attemptBlockingInterrupt
  // It's using Thread.interrupt and it's catching InterruptedException
  val aBlockingInterruptibleZIO = ZIO.attemptBlockingInterrupt {
    println(s"[${Thread.currentThread().getName}] running a long computation....")
    Thread.sleep(10000)
    42
  }


  // !!! The best way yo turn off a blocking computation is through a flag
  def interruptibleBlockingEffect(canceledFlag: AtomicBoolean): Task[Unit] =
    ZIO.attemptBlockingCancelable { // effect
      (1 to 100000).foreach{ element =>
        if (!canceledFlag.get()) {
          println(s"$element")
          Thread.sleep(100)
        }
      }
    } (ZIO.succeed(canceledFlag.set(true))) // cancelling/interrupting effect

  val interruptableBlocking = for {
    fib <- interruptibleBlockingEffect(new AtomicBoolean(false)).fork
    _ <- ZIO.sleep(2.seconds) *> ZIO.succeed("Interrupting...").debugThread *> fib.interrupt
    _ <- fib.join
  } yield ()

  // SEMANTIC blocking -> no blocking of threads, descheduling the effect / fiber
  // yield
  val sleeping = ZIO.sleep(1.second) // SEMANTICALLY blocking, interrupting
  val sleepingThread = ZIO.succeed(Thread.sleep(1000)) // blocking, uninterruptible

  // The yield can be send through ZIO.yieldNow

  val chainedZIO = (1 to 1000).map(i => ZIO.succeed(i)).reduce(_.debugThread *> _.debugThread)
  val yieldingDemo = (1 to 1000).map(i => ZIO.succeed(i)).reduce(_.debugThread *> ZIO.yieldNow *> _.debugThread)


  override def run = yieldingDemo
}
