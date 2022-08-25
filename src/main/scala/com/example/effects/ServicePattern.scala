package com.example.effects

import zio._

import java.util.Date

case class EventId(id: Long)

case class Event(name: String, data: Date, participants: Int)

object service {
  trait Logger {
    def info(message: String): ZIO[Any, Nothing, Unit]
  }

  trait Database {
    def get(sqlCommand: String): ZIO[Any, Throwable, Option[Event]]
  }

  trait Events {
    def get(id: EventId): ZIO[Any, Throwable, Option[Event]]
  }

  object Events {
    val live: ULayer[EventsLive.type] = ZLayer.succeed(EventsLive)
  }

  case class EventsLive(logger: Logger, db: Database) extends Events {
    override def get(id: EventId): ZIO[Any, Throwable, Option[Event]] = {
      logger.info(s"Getting event $id") *>
        db.get(s"SELECT * FROM events where id = $id")
    }
  }

}

object ServicePattern extends ZIOAppDefault {
  override def run: ZIO[Any with ZIOAppArgs with Scope, Any, Any] = ???
}
