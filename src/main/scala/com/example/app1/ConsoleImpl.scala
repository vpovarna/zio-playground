package com.example.app1

import zio._

object ConsoleImpl {
  lazy val live: ULayer[Console.ConsoleLive.type] = ZLayer.succeed(Console.ConsoleLive)
}
