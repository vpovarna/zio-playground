package com.example.app2

import zio._

object ZLayerPlaygroundApp extends ZIOAppDefault {

  final case class User(name: String, email: String)

  object UserService {
    trait UserEmailer {
      def notify(user: User, message: String): ZIO[Any, Throwable, Unit]
    }

    case class UserEmailerLive() extends UserEmailer {
      override def notify(user: User, message: String): ZIO[Any, Throwable, Unit] = ZIO.succeed {
        println(s"Sending message: $message to user: ${user.email}")
      }
    }

    object UserEmailer {
      lazy val live: ULayer[UserEmailer.type] = ZLayer.succeed(UserEmailer)
    }
  }

  object DI {

    import UserService._

    private val vladUser: User = User("vlad", "vlad@yahoo.com")

    lazy val program: ZIO[Any, Throwable, Unit] = for {
      _ <- UserEmailerLive.apply().notify(vladUser, "Test Email")
    } yield ()
  }

  override def run = {
    import UserService._

    DI.program.provideLayer(UserEmailer.live)
      .exitCode
  }
}
