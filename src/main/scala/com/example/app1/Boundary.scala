package com.example.app1

import zio._

trait Boundary {
  def doesGoogleHaveEvenAmountOfPicturesOf(topic: String): ZIO[Any, Nothing, Boolean]
}

object BoundaryImpl {
  lazy val live = ZLayer.fromFunction(make _)

  def make(google: Google): Boundary = new Boundary {
    override def doesGoogleHaveEvenAmountOfPicturesOf(topic: String): ZIO[Any, Nothing, Boolean] =
      google.countPicturesOf(topic).map(_ % 2 == 0)
  }
}
