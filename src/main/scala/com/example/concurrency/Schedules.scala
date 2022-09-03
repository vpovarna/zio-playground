package com.example.concurrency

import zio._
import com.example.utils._

object Schedules extends ZIOAppDefault {

  def aZIO(): ZIO[Any, String, String] = Random.nextBoolean.flatMap { flag =>
    if (flag) ZIO.succeed("fetch value!").debugThread
    else ZIO.succeed("failure...").debugThread *> ZIO.fail("error")
  }

  val aRetryZIO = aZIO().retry(Schedule.recurs(10)) // retries 10 times, return first success, last failure if all attempts were failures

  val oneTimeSchedule = Schedule.once
  val recurrentSchedule = Schedule.recurs(10)
  val fixedIntervalScheduled = Schedule.spaced(1.second) // retries every second until a success is returned.

  // Exponential backoff
  val exponentialBackoffSchedule = Schedule.exponential(1.second, 2.0)
  val fiboSchedule = Schedule.fibonacci(1.second) // 1 second 1 second 2 second 3 second 5 second

  // Combinators
  val recurentAndSpace = Schedule.recurs(3) && Schedule.spaced(1.second) // Scheduled 1 second apart, 3 attempts in total
  val recurrentThenSpaced = Schedule.recurs(3) ++ Schedule.spaced(1.second) // Scheduled 3 retries + retries every 1s, forever

  override def run = aZIO().retry(fixedIntervalScheduled)
}
