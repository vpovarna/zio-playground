package com.example.effects

import zio._

object ZIOApps {

  val meaningOfLife: UIO[Int] = ZIO.succeed(42)

  def main(args: Array[String]): Unit = {
    val runtime = Runtime.default
    implicit val trace: Trace = Trace.empty
    Unsafe.unsafeCompat(implicit unsafe => {
      val result = runtime.unsafe.run(meaningOfLife)

      println(result)
    })
  }
}

object BetterApp extends ZIOAppDefault {
  val meaningOfLife: UIO[Int] = ZIO.succeed(42)

  override def run: UIO[Int] = {

    // ZIOApps.meaningOfLife.flatMap(result => ZIO.succeed(println(result)))
    // alternative we can use debug
    ZIOApps.meaningOfLife.debug
  }
}

// Not Needed
object ManualApp extends ZIOApp {

  override implicit def environmentTag: zio.EnvironmentTag[ManualApp.type] = ???

  override type Environment = this.type

  override def bootstrap: ZLayer[ZIOAppArgs with Scope, Any, ManualApp.type] = ???

  override def run: ZIO[ManualApp.type with ZIOAppArgs with Scope, Any, Any] = ???
}