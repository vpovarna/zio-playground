package com.example.effects

import zio._

import java.io.IOException
import java.net.NoRouteToHostException
import scala.util.{Failure, Success, Try}

class ZIOErrorHandling extends ZIOAppDefault {

  // ZIOs can fail
  val aFailedZio: IO[String, Nothing] = ZIO.fail("Something went wrong!")
  val failedWithThrowableZIO: IO[RuntimeException, Nothing] = ZIO.fail(new RuntimeException("Boom!"))
  val failedWithDescription: IO[String, Nothing] = failedWithThrowableZIO.mapError(_.getMessage)

  // attempt: run an effect that might throw an exception
  val badZIO: ZIO[Any, Nothing, Int] = ZIO.succeed {
    println("Trying something")
    val string: String = null
    string.length
  } // this is bad. We need to be sure that the code passed to the succeed block will not fail, otherwise we'll send a wrong message to the user.


  // If we don't know it the code passed to ZIO fails or not, we should use attempt
  val anAttempt: ZIO[Any, Throwable, Int] = ZIO.attempt {
    println("Trying something")
    val string: String = null
    string.length
  }

  // How to effectfully catch error
  val catchError: ZIO[Any, Throwable, Any] = anAttempt.catchAll(e => ZIO.attempt(s"Returning a different value because: $e"))
  val catchSelectedErrors: ZIO[Any, Throwable, Any] = anAttempt.catchSome { // catchSome can retrieve a partial function
    case e: RuntimeException => ZIO.succeed(s"Ignoring runtime exceptions: $e")
    case _ => ZIO.succeed("Ignoring everything else")
  }

  // chain effects
  val aBetterAttempt = anAttempt.orElse(ZIO.succeed(56))

  // fold handle both success and failure -> a zio which can failed.
  val handleBetter: ZIO[Any, Nothing, String] = anAttempt.fold(
    ex => s"Something bad happened: $ex",
    value => s"Length of the string is: $value")

  // effectful fold: foldZio
  val handlerBoth_v2 = anAttempt.foldZIO(ex => ZIO.succeed(s"Something bad happened: $ex"), value => ZIO.succeed(s"Length of the string is: $value"))


  // conversions
  val ATryToZIO: Task[Int] = ZIO.fromTry(Try(42 / 2)) // can fail with Throwable

  val anEitherToZIO: ZIO[Any, Int, String] = ZIO.fromEither(Right("Success!"))
  // ZIO -> ZIO with Either as value in the channel

  val eitherZIO: URIO[Any, Either[Throwable, Int]] = anAttempt.either
  val anAttempt_v2: ZIO[Any, Throwable, Int] = eitherZIO.absolve

  val anOption: ZIO[Any, Option[Nothing], Int] = ZIO.fromOption(Some(42))

  /**
   * Exercises:Implement a version of fromTry, formEither, absolve using fold and foldZIO
   *
   */

  def try2ZIO[A](aTry: Try[A]): Task[A] = aTry match {
    case Success(value) => ZIO.succeed(value)
    case Failure(exception) => ZIO.fail(exception)
  }


  def either2ZIO[A, B](either: Either[A, B]): ZIO[Any, A, B] = either match {
    case Left(value) => ZIO.fail(value)
    case Right(value) => ZIO.succeed(value)
  }

  def option2ZIO[A](option: Option[A]): ZIO[Any, Option[Nothing], A] = option match {
    case Some(value) => ZIO.succeed(value)
    case None => ZIO.fail(None)
  }

  def zio2zioEither[R, A, B](zio: ZIO[R, A, B]): ZIO[R, Nothing, Either[A, B]] =
    zio.foldZIO(
      error => ZIO.succeed(Left(error)),
      value => ZIO.succeed(Right(value))
    )

  def absolveZIO[R, A, B](zio: ZIO[R, Nothing, Either[A, B]]): ZIO[R, A, B] = zio.flatMap {
    case Left(e) => ZIO.fail(e)
    case Right(v) => ZIO.succeed(v)
  }

  /**
   *
   * Errors == those failures that are present in the ZIO type signature ("checked" errors)
   * Defects == failures that are unrecoverable
   *
   * ZIO[R,E,A] can finish with Exit[E,A]
   * -- SUCCESS[A] containing a value
   * -- Cause[E]:
   *     - Fail[E] - containing the error
   *     - Die(t: Throwable) which was unforeseen
   */

  val failedInt: ZIO[Any, String, Int] = ZIO.fail("I failed")
  val failureCauseExpose: ZIO[Any, Cause[String], Int] = failedInt.sandbox
  val failureCauseHidden: ZIO[Any, String, Int] = failureCauseExpose.unsandbox

  // fold with cause
  val foldedWithCause = failedInt.foldCause(cause => s"this failed with ${cause.defects}", value => s"This succeeded with $value")
  val foldedWithCause_v2 = failedInt.foldCause(
    cause => ZIO.succeed(s"this failed with ${cause.defects}"),
    value => ZIO.succeed(s"This succeeded with $value"))

  /*
      Good Practice:
        - at a lower level, your "errors" should be treated
        - at a higher leve, you should hide "errors" and assume they are unrecoverable
   */

  def callHttpEndpoint(url: String): ZIO[Any, IOException, String] = ZIO.fail(new IOException("no internet, dummy!"))

  val endpointCallWithDefects: ZIO[Any, Nothing, String] = callHttpEndpoint("rockthejvm.com").orDie // all errors are now defects

  // refine the channel
  def callHttpEndpointWideError(url: String): ZIO[Any, Exception, String] = ZIO.fail(new IOException("No Internet!!!"))

  def callHTTPEndpoint_v2(url: String): ZIO[Any, IOException, String] = callHttpEndpointWideError(url).refineOrDie[IOException] {
    case e: IOException => e
    case _: NoRouteToHostException => new IOException()
  }

  // revers: turn the defects into the error channel
  val endpointCallWithError: ZIO[Any, String, String] = endpointCallWithDefects.unrefine {
    case e => e.getMessage
  }

  // Combine effects that has different error types in the signature

  // Application which fetch an index from a page and writes to DB
  trait AppError

  case class IndexError(message: String) extends AppError

  case class DBError(message: String) extends AppError

  val callApi: ZIO[Any, IndexError, String] = ZIO.succeed("page: <html></html>")
  val queryDB: ZIO[Any, DBError, Int] = ZIO.succeed(1)

  // combine callAPI with queryDB
  val combine: ZIO[Any, Product, (String, RuntimeFlags)] = for {
    page <- callApi
    rowsEffected <- queryDB
  } yield (page, rowsEffected) // Lost type safety

  /*
       Solutions:
         - design your error model. In out case, we can define an ApplicationError trait which can be implemented by both classes
         - scala 3 union type
         - mapError to some common error types
   */


  /**
   * Exercises
   *
   */
  // 1. Make this effect fail with a TYPED Error
  val aBadFailure: ZIO[Any, Nothing, Int] = ZIO.succeed[Int](throw new RuntimeException("this is bad!"))
  val aBetterFailure: ZIO[Any, Throwable, Int] = ZIO.attempt[Int](throw new RuntimeException("this is bad!"))
  val aBetterFailure_v1: ZIO[Any, Cause[Nothing], Int] = aBadFailure.sandbox
  val aBetterFailure_v2: ZIO[Any, Throwable, Int] = aBadFailure.unrefine {
    case e => e
  }

  // 2. Transform a ZIO into another ZIO with a narrower exception type
  def IOException[R, A](zio: ZIO[R, Throwable, A]): ZIO[R, IOException, A] =
    zio.refineOrDie[IOException] {
      case e: IOException => e
    }

  // 3.
  def left[R, E, A, B](zio: ZIO[R, E, Either[A, B]]): ZIO[R, Either[E, A], B] =
    zio.foldZIO(
      error => ZIO.fail(Left(error)),
      value => value match {
        case Left(e) => ZIO.fail(Right(e))
        case Right(v) => ZIO.succeed(v)
      }
    )

  // 4.
  val database = Map(
    "daniel" -> 123,
    "alice" -> 789
  )

  case class QueryError(reason: String)

  case class UserProfile(name: String, phone: Int)

  def lookupProfile(userId: String): ZIO[Any, QueryError, Option[UserProfile]] =
    if (userId != userId.toLowerCase()) {
      ZIO.fail(QueryError("user id format is invalid"))
    } else {
      ZIO.succeed(database.get(userId).map(phone => UserProfile(userId, phone)))
    }

  // surface out all the failed cases of this API
  def betterLookupProfile(userId: String): ZIO[Any, Option[QueryError], UserProfile] =
    lookupProfile(userId).foldZIO(
      error => ZIO.fail(Some(error)),
      profileOption => profileOption match {
        case Some(profile) => ZIO.succeed(profile)
        case None => ZIO.fail(None) // query returned no meaningful rows
      }
    )

  def betterLookupProfile_v2(userId: String): ZIO[Any, Option[QueryError], UserProfile] = lookupProfile(userId).some

  override def run: ZIO[Any with ZIOAppArgs with Scope, Any, Any] = ???
}
