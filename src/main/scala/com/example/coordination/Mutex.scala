package com.example.coordination

import zio._
import com.example.utils._
import scala.collection.immutable.Queue

abstract class Mutex {
  def acquire: UIO[Unit]

  def release: UIO[Unit]
}

object Mutex {
  type Signal = Promise[Nothing, Unit]

  case class State(locked: Boolean, waiting: Queue[Signal])

  val unlocked: State = State(locked = false, Queue())

  def make: UIO[Mutex] = Ref.make(unlocked).map { state =>
    new Mutex {
      override def acquire: UIO[Unit] = Promise.make[Nothing, Unit].flatMap { signal =>
        state.modify {
          case State(false, _) => ZIO.unit -> State(locked = true, Queue())
          case State(true, waiting) => signal.await -> State(locked = true, waiting = waiting.enqueue(signal))
        }.flatten
      }

      override def release: UIO[Unit] = state.modify {
        case State(false, _) => ZIO.unit -> unlocked
        case State(true, waiting) => if (waiting.isEmpty) ZIO.unit -> unlocked
        else {
          val (first, last) = waiting.dequeue
          first.succeed(()).unit -> State(locked = true, waiting = last)
        }
      }.flatten
    }
  }
}

object MutexPlayground extends ZIOAppDefault {

  def workInCriticalRegion(): UIO[Int] =
    ZIO.sleep(1.second) *> Random.nextIntBounded(100)

  def demoNonLockingTask(): ZIO[Any, Nothing, Unit] =
    ZIO.collectAllParDiscard((1 to 10).toList.map { i =>
      for {
        _ <- ZIO.succeed(s"s[task ${i}] working...").debugThread
        result <- workInCriticalRegion()
        _ <- ZIO.succeed(s"[task ${i}] got result: $result").debugThread
      } yield ()
    })

  def createTask(id: Int, mutex: Mutex): UIO[Int] = {
    val task = for {
      _ <- ZIO.succeed(s"[task $id] waiting for mutex ...").debugThread
      _ <- mutex.acquire
      // critical region start
      _ <- ZIO.succeed(s"s[task ${id}] working...").debugThread
      result <- workInCriticalRegion()
      _ <- ZIO.succeed(s"[task ${id}] got result: $result").debugThread
      // critical region end
      _ <- mutex.release
    } yield result

    task
      .onInterrupt(ZIO.succeed(s"[task $id] was interrupted.").debugThread)
      .onError(cause => ZIO.succeed(s"[task $id] ended in error: $cause"))
  }

  def demoLockingTask() = for {
    mutex <- Mutex.make
    _ <- ZIO.collectAllDiscard((1 to 10).toList.map { i =>
      createTask(i, mutex)
    })
  } yield ()

  def createInterruptingTask(id: Int, mutex: Mutex): UIO[Int] =
    if (id % 2 == 0) {
      createTask(id, mutex)
    } else for {
      fib <- createTask(id, mutex).fork
      _ <- ZIO.succeed(2500.millis) *> ZIO.succeed(s"interrupting task $id").debugThread *> fib.interrupt
      result <- fib.join
    } yield result

  override def run = demoLockingTask()
}