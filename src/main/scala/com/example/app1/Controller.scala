package com.example.app1

import zio._

import java.io.IOException

trait Controller {
  def run: IO[IOException, Unit]
}

object Controller {
  lazy val live: ZLayer[Boundary with Console, Nothing, ControllerLive] =
    ZLayer.fromFunction(ControllerLive.apply _)
}

case class ControllerLive(boundary: Boundary, console: Console) extends Controller {
  override def run: ZIO[Any, IOException, Unit] = for {
    _ <- console.printLine("-" * 100)

    cats <- boundary.doesGoogleHaveEvenAmountOfPicturesOf("cats")
    _ <- console.printLine(cats)

    dogs <- boundary.doesGoogleHaveEvenAmountOfPicturesOf("dogs")
    _ <- console.printLine(dogs)

    _ <- console.printLine("-" * 100)
  } yield ()
}

