package com.example.coordination

import zio._
import com.example.utils._

object Semaphores extends ZIOAppDefault {

  // A semaphore has an internal counter == permits which allocates a permit to a fiber.

  // Ex: Use case of a semaphore is to limit the number of concurrent sessions on a server.

  val aSemaphore = Semaphore.make(10)

  def doWorkWhileLogin(): UIO[Int] = ZIO.sleep(1.second) *> Random.nextIntBounded(100)

  def login(id: Int, sem: Semaphore): UIO[Int] =
    ZIO.succeed(s"[task $id] waiting to log in.").debugThread *>
      sem.withPermit { // acquire + zio + release
        for {
          // critical section start
          _ <- ZIO.succeed(s"[task $id] logged in, working....").debugThread
          res <- doWorkWhileLogin()
          _ <- ZIO.succeed(s"[task $id] done: $res").debugThread
        } yield res
      }

  def demoSemaphore() = for {
    sem <- Semaphore.make(2) // Semaphore.make(1) == a Mutex
    f1 <- login(1, sem).fork
    f2 <- login(2, sem).fork
    f3 <- login(3, sem).fork
    _ <- f1.join
    _ <- f2.join
    _ <- f3.join
  } yield ()

  def loginWighted(n: Int, sem: Semaphore): UIO[Int] =
    ZIO.succeed(s"[task $n] waiting to log in with $n permits").debugThread *>
      sem.withPermits(n) { // acquire + zio + release
        for {
          // critical section start
          _ <- ZIO.succeed(s"[task $n] logged in, working....").debugThread
          res <- doWorkWhileLogin()
          _ <- ZIO.succeed(s"[task $n] done: $res").debugThread
        } yield res
      }
  def demoSemaphoreWeights() = for {
    sem <- Semaphore.make(2)
    f1 <- loginWighted(1, sem).fork
    f2 <- loginWighted(2, sem).fork
    f3 <- loginWighted(3, sem).fork // Will block forever
    _ <- f1.join
    _ <- f2.join
    _ <- f3.join
  } yield ()


  override def run = demoSemaphoreWeights()
}
