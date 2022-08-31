package com.example.concurrency

import com.example.concurrency.Resources.openFileScanner
import zio._
import com.example.utils._

import java.io.File
import java.util.Scanner

object Resources extends ZIOAppDefault {
  // open and close resources

  // finalizers
  def unsafeMethod() = throw new RuntimeException("Not an int here for you!")

  val onAttempt: ZIO[Any, Throwable, Nothing] = ZIO.attempt(unsafeMethod())

  // finalizers
  // ensuring returns another ZIO, so we can combine them.
  val attemptWithFinalizer: ZIO[Any, Throwable, Nothing] = onAttempt.ensuring(ZIO.succeed("finalizer!").debugThread)
  val attemptWithFinalizer_v2 = attemptWithFinalizer.ensuring(ZIO.succeed("another finalizer "))

  // Finalizers are running before the code is run.
  // There are dedicated finalizers which can be run in case of onError, onInterruptions, onExist, onDone etc

  // General use case for finalizers it's to manage resource lifecycle

  class Connection(url: String) {
    def open() = ZIO.succeed(s"opening connection to $url...").debugThread

    def close() = ZIO.succeed(s"closing connection to $url...").debugThread
  }

  object Connection {
    def create(url: String) = ZIO.succeed(new Connection(url))
  }

  // example simulates a resource leak
  val fetchUrl = for {
    conn <- Connection.create("rockthejvm.com")
    fib <- (conn.open *> ZIO.sleep(300.seconds)).fork
    _ <- ZIO.sleep(1.second) *> ZIO.succeed("interrupting").debugThread *> fib.interrupt // interrupting the fib which open the connection from another thread.
    _ <- fib.join
  } yield ()

  // Ensuring will ensure that the resource is released
  val correctFetchUrl = for {
    conn <- Connection.create("rockthejvm.com")
    fib <- (conn.open() *> ZIO.sleep(300.seconds)).ensuring(conn.close()).fork
    _ <- ZIO.sleep(1.second) *> ZIO.succeed("interrupting").debugThread *> fib.interrupt // interrupting the fib which open the connection from another thread.
    _ <- fib.join
  } yield () // preventing leaks

  // pattern acquireRelease => using this definition you specify how you want to acquire connection and how you wan to release it using the finalizer method which is executed no matter what.
  // You don't really need to care about anything else.
  /*
      acquireRelease:
        - acquire cannot be interrupted
        - all finalizers are guaranteed to run
   */
  val cleanConnection = ZIO.acquireRelease(Connection.create("rockthejvm.com"))((f: Connection) => f.close())

  val fetchWithResources = for {
    conn <- cleanConnection
    fib <- (conn.open() *> ZIO.sleep(300.seconds)).fork
    _ <- ZIO.sleep(1.second) *> ZIO.succeed("interrupting").debugThread *> fib.interrupt // interrupting the fib which open the connection from another thread.
    _ <- fib.join
  } yield ()

  // Will eliminate the Scope. This resource will have the finite scope.
  val fetchWithScopeResource: ZIO[Any, Nothing, Unit] = ZIO.scoped(fetchWithResources)

  // acquireReleaseWith = This is a ZIO which can't throws errors and return Unit
  val cleanConnection_v2: ZIO[Any, Nothing, Unit] = ZIO.acquireReleaseWith(
    Connection.create("rockthejvm") // acquire
  )(
    _.close() // release
  )(
    conn => conn.open() *> ZIO.sleep(300.seconds) // use
  )

  val fetchWithResources_v2 = for {
    fib <- cleanConnection_v2.fork
    _ <- ZIO.sleep(1.second) *> ZIO.succeed("interrupting").debugThread
    _ <- fib.join
  } yield ()

  /*
       Exercises
   */

  // 1. Use acquireRelease pattern to open a file, print all lines, (one every 100 millis), then close the file.

  def openFileScanner(path: String): UIO[Scanner] =
    ZIO.succeed(new Scanner(new File(path)))

  def readLineByLIne(scanner: Scanner): UIO[Unit] = {
    if (scanner.hasNextLine)
      ZIO.succeed(scanner.nextLine()).debugThread *> ZIO.sleep(100.millis) *> readLineByLIne(scanner)
    else
      ZIO.unit
  }

  def acquireOpenFile(path: String): UIO[Unit] = {
    ZIO.succeed(s"opening file $path").debugThread *> ZIO.acquireReleaseWith(
      openFileScanner(path) // acquire
    )(
      scanner => ZIO.succeed(s"Closing file at $path").debugThread *> ZIO.succeed(scanner.close())
    )(
      {
        readLineByLIne // usage effect
      }
    )
  }

  // testing acquisition
  val testInterruptFileDebug = for {
    fib <- acquireOpenFile("src/main/scala/com/example/concurrency/Resources.scala").fork
    _ <- ZIO.sleep(2.seconds) *> fib.interrupt
  } yield ()


  // acquireRelease vs acquireReleaseWith
  def connFromConfig(path: String): UIO[Unit] =
    ZIO.acquireReleaseWith(openFileScanner(path))(scanner => ZIO.succeed(s"Closing file at $path").debugThread *> ZIO.succeed(scanner.close())) {
      scanner =>
        ZIO.acquireReleaseWith(Connection.create(scanner.nextLine()))(_.close()) { conn =>
          conn.open() *> ZIO.never
        }
    }

  // !!!! acquireRelease is better for nested resources
  def connectionFromConfig_v2(path: String): UIO[Unit] = ZIO.scoped {
    for {
      scanner <- ZIO.acquireRelease(openFileScanner(path))(scanner => ZIO.succeed(s"Closing file at $path").debugThread *> ZIO.succeed(scanner.close()))
      conn <- ZIO.acquireRelease(Connection.create(scanner.nextLine()))(_.close())
      _ <- conn.open() *> ZIO.never
    } yield ()
  }

  //v3
  def connectionFromConfig_v3(path: String): UIO[Unit] = ZIO.scoped {
    for {
      scanner <- ZIO.acquireRelease(openFileScanner(path))(scanner => ZIO.succeed(s"Closing file at $path").debugThread *> ZIO.succeed(scanner.close()))
      _ <- readLineByLIne(scanner)
    } yield ()
  }

  override def run = connectionFromConfig_v3("src/main/scala/com/example/concurrency/Resources.scala")
}
