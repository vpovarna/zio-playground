package com.example.streams

import zio._
import zio.stream._

import java.io.{IOException, InputStream}

object ZIOStreams extends ZIOAppDefault {

  // effect
  val aSuccess: ZIO[Any, Nothing, Int] = ZIO.succeed(42)

  // ZStream == collection of zero or maybe infinite elements. Like Source
  val aStream: ZStream[Any, Nothing, Int] = ZStream.fromIterable(1 to 10)
  val intStream: ZStream[Any, Nothing, Int] = ZStream(1, 2, 3, 4, 5)
  val stringStream: ZStream[Any, Nothing, String] = intStream.map(_.toString)

  // Sink == destination of your elements
  val sum: ZSink[Any, Nothing, Int, Nothing, Int] = ZSink.sum[Int]
  val take5: ZSink[Any, Nothing, Int, Int, Chunk[Int]] = ZSink.take(5)
  val take5Map: ZSink[Any, Nothing, Int, Int, Chunk[String]] = take5.map(chunk => chunk.map(_.toString()))

  // leftovers. The first chunk is the output and the second value of the tupple is the leftover
  val take5Leftovers: ZSink[Any, Nothing, Int, Nothing, (Chunk[String], Chunk[Int])] = take5Map.collectLeftover
  val take5Ignore: ZSink[Any, Nothing, Int, Nothing, Chunk[String]] = take5Map.ignoreLeftover

  // contramap
  val take5Strings: ZSink[Any, Nothing, String, Int, Chunk[Int]] = take5.contramap(_.toInt)

  // Composition between Source / Sink
  val zio: ZIO[Any, Nothing, Int] = intStream.run(sum)

  // ZPipeline == transformer / processor of elements between source and sink
  val stringToInt: ZPipeline[Any, Nothing, String, Int] = ZPipeline.map(_.toInt)
  val zio_v2: ZIO[Any, Nothing, Int] = stringStream.via(stringToInt).run(sum)

  // many pipelines
  val filterLogic: ZPipeline[Any, Nothing, Int, Int] = ZPipeline.filter(_ % 2 == 0)
  val appLogic: ZPipeline[Any, Nothing, String, Int] = stringToInt >>> filterLogic

  val zio_v3: ZIO[Any, Nothing, Int] = stringStream.via(appLogic).run(sum)

  /**
   * Failures / errors
   *
   */

  // fail a stream manually
  val failSource: ZStream[Any, String, Int] = ZStream(1, 2) ++ ZStream.fail("Something bad") ++ ZStream(3, 4, 5)

  class FakeInputStream[T <: Throwable](limit: Int, failAt: Int, failWith: => T) extends InputStream {
    val data: Array[Byte] = "0123456789".getBytes
    var counter: Int = 0
    var index: Int = 0

    override def read(): Int = {
      if (counter == limit) -1 // stream end
      else if (counter == failAt) throw failWith
      else {
        val result = data(index)
        index = (index + 1) % data.length
        counter += 1
        result
      }
    }
  }

  //. hide side effects and extract it as effect
  val nonFailStream: ZStream[Any, IOException, String] =
    ZStream.fromInputStream(new FakeInputStream(12, 99, new IOException("Something Bad")), chunkSize = 1)
      .map(byte => new String(Array(byte)))

  val sink: ZSink[Any, Nothing, String, Nothing, String] =
    ZSink.collectAll[String].map(_.mkString("-"))

  val failingStream: ZStream[Any, IOException, String] =
    ZStream.fromInputStream(new FakeInputStream(12, 5, new IOException("Something Bad")), chunkSize = 1)
      .map(byte => new String(Array(byte)))

  val defectStream: ZStream[Any, IOException, String] =
    ZStream.fromInputStream(new FakeInputStream(12, 5, new RuntimeException("Something not seen")), chunkSize = 1)
    .map(byte => new String(Array(byte)))

  // recovery

  val recoveryStream: ZStream[Any, Throwable, String] = ZStream("a", "b", "c")

  // continue at the point of failure
  val recoveredEffect = failingStream.orElse(recoveryStream).run(sink)

  // orElseEither will send the remaining elements to the left and the recover elements to the right
  val recoverEffectWithEither = failingStream.orElseEither(recoveryStream)


  // catch
  val caughtErrors = failingStream.catchSome{
    case _: IOException => recoveryStream
  }

  // catch some cause

  val errorContained: ZStream[Any, Nothing, Either[IOException, String]] = failingStream.either

  override def run = errorContained.run(ZSink.collectAll).debug

}
