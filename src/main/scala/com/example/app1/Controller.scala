package com.example.app1

import zio._

import java.io.IOException

trait Controller {
  def run: IO[IOException, Unit]
}

object ControllerImpl {
  lazy val live = ZLayer.fromFunction(make _)

  def make(boundary: Boundary, console: Console): Controller = new Controller {
    override def run: ZIO[Any, IOException, Unit] = for {
      _ <- console.printLine("-" * 100)

      cats <- boundary.doesGoogleHaveEvenAmountOfPicturesOf("cats")
      _ <- console.printLine(cats)

      dogs <- boundary.doesGoogleHaveEvenAmountOfPicturesOf("dogs")
      _ <- console.printLine(dogs)

      _ <- console.printLine("-" * 100)
    } yield ()
  }

}
