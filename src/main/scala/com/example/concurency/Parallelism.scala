package com.example.concurency

import zio._
import com.example.utils._

import scala.io.Source

object Parallelism extends ZIOAppDefault {
  // two parallel effect might run at the same time

  val meaningOfLife = ZIO.succeed(42)
  val favLanguage = ZIO.succeed("Scala")
  val compined = meaningOfLife.zip(favLanguage) // will combine both ZIOs sequentially

  // combine two ZIOs in parallel
  val combinedPar = meaningOfLife.zipPar(favLanguage) // the end effect will be an (Int, String); th combination is parallel. Each effect will be run o each own fibber

  /*
     How zipPar is implemented
     Questions:
       - what if one fails? // the other should be interrupted
       - what if one is interrupted? // the entre thing should be interrupted
       - what if the whole thing is interrupted? // need to interrupt both effects
   */

  def myZipPar[R, E, A, B](zioa: ZIO[R, E, A], ziob: ZIO[R, E, B]): ZIO[R, E, (A, B)] = {
    val exits = for {
      fiba <- zioa.fork
      fibb <- ziob.fork

      exita <- fiba.await
      exitb <- exita match {
        case Exit.Success(_) => fibb.await
        case Exit.Failure(_) => fibb.interrupt
      }
    } yield (exita, exitb)

    exits.flatMap {
      case (Exit.Success(a), Exit.Success(b)) => ZIO.succeed(a,b)
      case (Exit.Success(_), Exit.Failure(cause)) => ZIO.failCause(cause)
      case (Exit.Failure(cause), Exit.Success(_)) => ZIO.failCause(cause)
      case (Exit.Failure(c1), Exit.Failure(c2)) => ZIO.failCause(c1 && c2)
    }
  }

  // parallel combinator:
  // zipPar, zipWithPar

  // collectAllPar
  val effects: Seq[ZIO[Any, Nothing, Int]] = (1 to 10).map(i => ZIO.succeed(i).debugThread)
  val collectedValues: ZIO[Any, Nothing, Seq[Int]] = ZIO.collectAllPar(effects) // "traverse"
  // collectAllPar => keeps the order of the created effects

  // foreachPar
  val printlnParallel: ZIO[Any, Nothing, List[Unit]] = ZIO.foreachPar((1 to 10).toList)(i => ZIO.succeed(println(i)))

  // reduceAllPar, mergeAppPar
  val sumPar: ZIO[Any, Nothing, Int] = ZIO.reduceAllPar(ZIO.succeed(0), effects)(_ + _)
  val sumPar_v2: ZIO[Any, Nothing, Int] = ZIO.mergeAllPar(effects)(0)(_ + _)

  /**
   *  If all the effects succeed, all good
   *  If one effect failed => everyone else is interrupted, error is surfaced
   */

    /*
        Exercise: do the word counting using the combinators
     */

    // An effect which read a file and count all words
    def countWords(path: String): ZIO[Any, Nothing, Int] = {
      ZIO.succeed {
        val source = Source.fromFile(path)
        val wordCount = source
          .getLines()
          .mkString(" ")
          .split(" ")
          .count(_.nonEmpty)
        println(s"Counting $wordCount in $path")
        source.close()
        wordCount
      }
    }


  val countWordsFiles: Seq[ZIO[Any, Nothing, Int]] = (1 to 10).map(i => s"src/main/resources/testFile_$i.txt")
    .map(countWords)

  // 1. collectAllPar
  val result_v1: ZIO[Any, Nothing, Int] = ZIO.collectAllPar(countWordsFiles)
    .map(_.sum)

  // 2. reduceAllPar
  val result_v2: ZIO[Any, Nothing, Int] = ZIO.reduceAllPar(ZIO.succeed(0), countWordsFiles)(_ + _)

  // 3. mergeAllPar
  val result_v3: ZIO[Any, Nothing, Int] = ZIO.mergeAllPar(countWordsFiles)(0)(_ + _)


  override def run =  result_v3.debugThread
}
