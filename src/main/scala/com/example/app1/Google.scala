package com.example.app1

import zio._

trait Google {
  def countPicturesOf(topic: String): ZIO[Any, Nothing, Int]
}

object GoogleImpl {
  lazy val live = ZLayer.succeed(make)

  def make: Google = new Google {
    override def countPicturesOf(topic: String): ZIO[Any, Nothing, Int] = ZIO.succeed(if (topic == "cats") 1337 else 1338)
  }
}

