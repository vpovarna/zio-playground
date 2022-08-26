package com.example.app1

import zio._

object Main extends ZIOAppDefault {
  override def run: URIO[Any, ExitCode] = Program.run.exitCode
}
