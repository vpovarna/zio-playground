package com.example.app1

import zio._

import java.io.IOException

object Program {
  lazy val run: ZIO[Any, IOException, Unit] = make().flatMap(_.run)

  def make(): ZIO[Any, Nothing, Controller] =
    ZIO
      .service[Controller]
      .provide(
        Controller.live,
        Boundary.live,
        Google.live,
        ConsoleImpl.live
      )
}
