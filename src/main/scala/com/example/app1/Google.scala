package com.example.app1

import zio._

trait Google {
  def countPicturesOf(topic: String): ZIO[Any, Nothing, Int]
}

object Google {
  lazy val live: ULayer[GoogleImpl] = ZLayer.succeed(GoogleImpl.apply())
}

case class GoogleImpl() extends Google {
  override def countPicturesOf(topic: String): ZIO[Any, Nothing, Int] =
    ZIO.succeed(if (topic == "cats") 1337 else 1338)
}

