package com.example.app1

import zio._

object Program {
  lazy val run = make().flatMap(_.run)

  def make(): ZIO[Any, Nothing, Controller] =
    ZIO
      .service[Controller]
      .provide(
        Controller.live,
        Boundary.live,
        Google.live,
        ConsoleImpl.live,
        ZLayer.Debug.tree,
      )
}
