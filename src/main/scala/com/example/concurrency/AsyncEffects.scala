package com.example.concurrency

import zio._
import com.example.utils._

import java.util.concurrent.{ExecutorService, Executors}
import scala.concurrent.impl.Promise
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

object AsyncEffects extends ZIOAppDefault {

  // Async is callback based.

  object LoginService {
    case class AuthError(message: String)

    case class UserProfile(email: String, name: String)

    // thread pool
    val executors: ExecutorService = Executors.newFixedThreadPool(8)

    // database
    val passwd = Map(
      "daniel@rockthejvm.com" -> "RockTheJvm1!"
    )

    // the profile data
    val database = Map(
      "daniel@rockthejvm.com" -> "Daniel"
    )

    def login(email: String, password: String)(onSuccess: UserProfile => Unit, onFailure: AuthError => Unit): Unit =
      executors.execute { () =>
        println(s"[${Thread.currentThread().getName}] Attempting login for $email")
        passwd.get(email) match {
          case Some(`password`) => onSuccess(UserProfile(email, database(email)))
          case Some(_) => onFailure(AuthError("Incorrect Password"))
          case None => onFailure(AuthError("User doesn't exist"))
        }

      }
  }

  // ZIOs gives us the possibility to lift these async APIs as ZIOs

  def loginAsZio(id: String, pw: String): ZIO[Any, LoginService.AuthError, LoginService.UserProfile] =
    ZIO.async { cb /* callback object which is created by zero and can be invoke on the error / profile */ =>
      LoginService.login(id, pw)(
        profile => cb(ZIO.succeed(profile)),
        error => cb(ZIO.fail(error))
      )
    } // if ypu don't use the cb, the ZIO will blocked forever.

  val loginProgram = for {
    email <- Console.readLine("Email: ")
    pass <- Console.readLine("Password: ")
    profile <- loginAsZio(email, pass).debugThread
    _ <- Console.printLine(s"Welcome to rock the JVM, ${profile.name}")
  } yield ()

  /*
      Exercise
   */

  // 1. Lift a computation running on some (external) thread to ZIO

  def external2ZIO[A](computation: () => A)(executors: ExecutorService): Task[A] =
    ZIO.async { cb =>
      executors.execute { () =>
        try {
          val result = computation()
          cb(ZIO.succeed(result))
        } catch {
          case e: Throwable => cb(ZIO.fail(e))
        }
      }
    }

  def demoExternal2ZIO() = {
    val executors = Executors.newFixedThreadPool(8)
    val zio = external2ZIO { () =>
      println(s"[${Thread.currentThread().getName}] computing the meaning of life on some thread")
      Thread.sleep(1000)
      42
    }(executors)

    zio.debugThread.unit
  }

  // 2 - lift a Future to ZIO

  def future2ZIO[A](future: => Future[A])(implicit ec: ExecutionContext): Task[A] =
    ZIO.async {cb =>
      future.onComplete{
        case Success(value) => cb(ZIO.succeed(value))
        case Failure(e) => cb(ZIO.fail(e))
      }
    }

  def demoFuture2ZIO() = {
    val executors = Executors.newFixedThreadPool(8)
    implicit val ec = ExecutionContext.fromExecutorService(executors)
    val zio = future2ZIO {
      Future {
        println(s"[${Thread.currentThread().getName}] computing the meaning of life on some thread")
        Thread.sleep(1000)
        42
      }
    }

    zio.debugThread.unit
  }

  // 3 - infinite async effect
  def neverEndingZIO[A]: UIO[A] = ZIO.async{ cb => ()} // callback is never invoked.

  // API in ZIO
  val neverEndingZIOv2: UIO[Unit] = ZIO.never

  override def run = demoFuture2ZIO().exitCode
}
