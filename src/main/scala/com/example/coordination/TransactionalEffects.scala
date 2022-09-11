package com.example.coordination

import zio._
import zio.stm._
import com.example.utils._

object TransactionalEffects extends ZIOAppDefault {

  // STM  = "atomic effects"
  val anSTM: ZSTM[Any, Nothing, Int] = STM.succeed(42)
  val aFailedSTM = STM.fail("something bad")
  val anAttemptSTM = STM.attempt(42 / 0)
  // map, flatmap, for comprehensions

  // type aliases
  val ustm: USTM[Int] = STM.succeed(2)
  val anSTMv2: STM[Nothing, Int] = STM.succeed(42)

  // STM vs ZIO = description of an atomic effect. the evaluation is fully atomic
  // Evaluation of an STM is a ZIO

  // Similar with a transaction
  val anAtomicEffect: ZIO[Any, Throwable, Int] = anAttemptSTM.commit


  // example
  def transferMoney(sender: Ref[Long], receiver: Ref[Long], amount: Long): ZIO[Any, String, Long] = {
    for {
      senderBalance <- sender.get
      _ <- if (senderBalance < amount) ZIO.fail("Transfer failed: Insufficient founds.") else ZIO.unit
      _ <- sender.update(_ - amount)
      _ <- receiver.update(_ + amount)
      newBalance <- sender.get
    } yield newBalance
  }

  def exploitBuggyBank(): ZIO[Any, String, Unit] = for {
    sender <- Ref.make(1000L)
    receiver <- Ref.make(0L)
    // the problem is that transferMoney is not atomic
    fib1 <- transferMoney(sender, receiver, 1000).fork
    fib2 <- transferMoney(sender, receiver, 1000).fork
    _ <- (fib1 zip fib2).join
    _ <- receiver.get.debugThread
  } yield ()

  def loop(i: Int): ZIO[Any, String, Unit] = {
    if (i > 10000)
      ZIO.unit
    else exploitBuggyBank() *> loop(i + 1)
  }

  def transferMoneyTransactional(sender: TRef[Long], receiver: TRef[Long], amount: Long): STM[String, Long] = {
    for {
      senderBalance <- sender.get
      _ <- if (senderBalance < amount) STM.fail("Transfer failed: Insufficient founds.") else STM.unit
      _ <- sender.update(_ - amount)
      _ <- receiver.update(_ + amount)
      newBalance <- sender.get
    } yield newBalance
  }

  def cannotExploit(): ZIO[Any, String, Unit] = for {
    sender <- TRef.make(1000L).commit
    receiver <- TRef.make(0L).commit
    // the problem is that transferMoney is not atomic
    fib1 <- transferMoneyTransactional(sender, receiver, 1000).commit.fork
    fib2 <- transferMoneyTransactional(sender, receiver, 1000).commit.fork
    _ <- (fib1 zip fib2).join
    _ <- receiver.get.commit.debugThread
  } yield ()

  def loopTransaction(effect: ZIO[Any, String, Unit], i: Int): ZIO[Any, String, Unit] = {
    if (i > 10000)
      effect.ignore
    else effect.ignore *> loopTransaction(effect, i + 1)
  }

  // Atomic variables: TRef
  // same api: get, update, modify, set

  //TArray == all the operations are atomic
  val specifiedValuesTArray: USTM[TArray[Int]] = TArray.make(1, 2, 3)
  val iterableArray = TArray.fromIterable(List(1, 2, 3, 4, 5))

  // get / apply to get a value from array
  val tArrayGetElem = for {
    tArray <- iterableArray
    elem <- tArray(2)
  } yield elem

  // update
  val tArrayUpdateElem = for {
    tArray <- iterableArray
    _ <- tArray.update(1, el => el + 10)
  } yield tArray

  // transform
  val transformArray:
  for {
    tArray <- iterableArray
    _ <- tArray.transform(_ * 10)
  } yield tArray

  // TSet
  // create
  val specificValueTSet = TSet.make(1, 2, 3, 4, 5, 6, 7)
  // contains
  val tSetContainsElem: STM[Nothing, Boolean] = for {
    tSet <- specificValueTSet
    res <- tSet.contains(3)
  } yield res

  // add
  val putElem: USTM[TSet[Int]] = for {
    tSet <- specificValueTSet
    _ <- tSet.put(7)
  } yield tSet

  // delete
  val deleteElem = for {
    tSet <- specificValueTSet
    _ <- tSet.delete(1)
  } yield tSet


  // TMap
  val aTMapEffect = TMap.make(("Daniel", 1234), ("Alice", 456), ("QE2", 999))

  // put
  val putElemTMap = for {
    tMap <- aTMapEffect
    _ <- tMap.put("Master Yoda", 123)
  } yield tMap

  // get
  val getElemTMap = for {
    tmap <- aTMapEffect
    elem <- tmap.get("Daniel")
  } yield elem

  // TQueue
  val tQueueBounded = TQueue.bounded[Int](5)
  // offer / offerAll
  val demoOffer = for {
    tQueue <- tQueueBounded
    _ <- tQueue.offerAll(List(1, 2, 3, 4, 5, 6))
  } yield tQueue

  // take / takeAll
  val demoTakeAll = for {
    tQueue <- demoOffer
    elements <- tQueue.takeAll
  } yield elements

  // takeOption, peek
  // toList, toVector


  // TPromise
  val tPromise: USTM[TPromise[String, Int]] = TPromise.make[String, Int]
  // await
  val tPromiseAwait = for {
    p <- tPromise
    result <- p.await
  } yield result

  val demoSucceed = for {
    p <- tPromise
    _ <- p.succeed(100)
  } yield ()

  // semaphore

  override def run: ZIO[Any with ZIOAppArgs with Scope, Any, Any] =
  //loop(1)
    loopTransaction(cannotExploit(), 1)
}
