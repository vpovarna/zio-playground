package com.example.app1

import zio._

trait Boundary {
  def doesGoogleHaveEvenAmountOfPicturesOf(topic: String): ZIO[Any, Nothing, Boolean]
}

object Boundary {
  lazy val live = ZLayer.fromFunction(BoundaryLive.apply _)
}

case class BoundaryLive(google: Google) extends Boundary {
  override def doesGoogleHaveEvenAmountOfPicturesOf(topic: String): ZIO[Any, Nothing, Boolean] =
    google.countPicturesOf(topic).map(_ % 2 == 0)
}

