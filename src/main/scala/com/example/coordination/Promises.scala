package com.example.coordination

import zio._
import com.example.utils._

object Promises extends ZIOAppDefault {

  val aPromise: UIO[Promise[Throwable, RuntimeFlags]] = Promise.make[Throwable, Int]

  // Operations:
  // 1 await = block a fiber until the promise has a value

  val reader = aPromise.flatMap { promise =>
    promise.await
  }

  // succeed, fail, complete
  val writer = aPromise.flatMap { promise =>
    promise.succeed(42)
  }

  // demo

  def demoPromise(): Task[Unit] = {
    // producer - consumer problem
    def consumer(promise: Promise[Throwable, Int]): Task[Unit] = for {
      _ <- ZIO.succeed("[consumer] waiting for result...").debugThread
      mol <- promise.await
      _ <- ZIO.succeed(s"[consumer] I got the result: $mol").debugThread
    } yield ()

    def producer(promise: Promise[Throwable, Int]): Task[Unit] = for {
      _ <- ZIO.succeed(s"[producer] generating numbers ...").debugThread
      _ <- ZIO.sleep(3.seconds)
      _ <- ZIO.succeed(s"[producer] complete.").debugThread
      mol <- ZIO.succeed(42)
      _ <- promise.succeed(mol)
    } yield ()

    for {
      promise <- Promise.make[Throwable, Int]
      _ <- consumer(promise) zipPar producer(promise)
    } yield ()
  }

  // simulating downloading file
  val fileParts = List("I ", "love S", "cala", " with pure FP an", "d ZIO! <EOF>")


  def downloadFileWithRef() = {

    def downloadFile(contentRef: Ref[String]): UIO[Unit] =
      ZIO.collectAllDiscard(
        fileParts.map { part =>
          ZIO.succeed(s"got '$part'").debugThread *> ZIO.sleep(1.second) *> contentRef.update(_ + part)
        }
      )

    def notifyFileComplete(contentRef: Ref[String]): UIO[Unit] = for {
      file <- contentRef.get
      _ <- if (file.endsWith("<EOF>")) ZIO.succeed("File download complete.").debugThread
      else ZIO.succeed("Downloading....").debugThread *> ZIO.sleep(500.millis) *> notifyFileComplete(contentRef)
    } yield ()

    for {
      contentRef <- Ref.make("")
      _ <- downloadFile(contentRef) zipPar notifyFileComplete(contentRef)
    } yield ()

  }

  def downloadFileWithRefPromise() = {

    def downloadFile(contentRef: Ref[String], promise: Promise[Throwable, String]): Task[Unit] =
      ZIO.collectAllDiscard(
        fileParts.map { part =>
          for {
            _ <- ZIO.succeed(s"got '$part'").debugThread
            _ <- ZIO.sleep(1.second)
            file <- contentRef.updateAndGet(_ + part)
            _ <- if (file.endsWith("<EOF>")) promise.succeed(file)
            else ZIO.unit
          } yield ()
        }
      )


    def notifyFileComplete(contentRef: Ref[String], promise: Promise[Throwable, String]): Task[Unit] = for {
      _ <- ZIO.succeed("downloading ... ").debugThread
      file <- promise.await
      _ <- ZIO.succeed(s"file download complete: $file").debugThread
    } yield ()

    for {
      contentRef <- Ref.make("")
      promise <- Promise.make[Throwable, String]
      _ <- downloadFile(contentRef, promise) zipPar notifyFileComplete(contentRef, promise)
    } yield ()

  }

  /**
   *
   * Ex
   */

  // Simulate an 'egg boiler' with two ZIOs

  def eggBoiler(): UIO[Unit] = {
    def eggReady(signal: Promise[Nothing, Unit]): UIO[Unit] = for {
      _ <- ZIO.succeed("Egg boiler on some other fiber, waiting... ").debugThread
      _ <- signal.await
      _ <- ZIO.succeed("Egg Ready!").debugThread
    } yield ()

    def tickingClock(ticks: Ref[Int], promise: Promise[Nothing, Unit]): UIO[Unit] =
      for {
        _ <- ZIO.sleep(1.second)
        tick <- ticks.getAndUpdate(_ + 1)
        _ <- ZIO.succeed(s"Counting $tick").debugThread
        _ <- if (tick >= 10) promise.succeed(())
        else tickingClock(ticks, promise)
      } yield ()


    for {
      contentRef <- Ref.make(0)
      promise <- Promise.make[Nothing, Unit]
      _ <- eggReady(promise) zipPar tickingClock(contentRef, promise)
    } yield ()
  }

  override def run = eggBoiler()
}
