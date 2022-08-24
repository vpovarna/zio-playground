package com.example.effects

import zio._

import java.util.concurrent.TimeUnit

object ZIADependencies extends ZIOAppDefault {

  // Pass dependencies to ZIO Effects

  // application to subscribe users to newsletter

  case class User(name: String, email: String)

  class UserSubscription(emailService: EmailService, userDatabase: UserDatabase) {
    def subscribeUser(user: User): ZIO[Any, Throwable, Unit] = {
      for {
        _ <- emailService.email(user)
        _ <- userDatabase.insert(user)
      } yield ()
    }
  }

  object UserSubscription {
    def create(emailService: EmailService, userDatabase: UserDatabase) = new UserSubscription(emailService, userDatabase)

    val live: ZLayer[EmailService with UserDatabase, Nothing, UserSubscription] = ZLayer.fromFunction(create _)
  }

  class EmailService {
    def email(user: User): ZIO[Any, Throwable, Unit] =
      ZIO.succeed(println(s"You've just been subscribed to Rock the JVM. Welcome: ${user.name}"))
  }

  object EmailService {
    def create() = new EmailService

    val live: ZLayer[Any, Nothing, EmailService] = ZLayer.succeed(create())
  }

  class UserDatabase(connectionPool: ConnectionPool) {
    def insert(user: User): ZIO[Any, Throwable, Unit] = {
      for {
        conn <- connectionPool.get
        _ <- conn.runQuery(s"insert into subscribers(name, email) values (${user.name}, ${user.email})")
      } yield ()
    }
  }

  object UserDatabase {
    def create(connectionPool: ConnectionPool) = new UserDatabase(connectionPool)

    val live: ZLayer[ConnectionPool, Nothing, UserDatabase] = ZLayer.fromFunction(connectionPool => create(connectionPool))
  }

  class ConnectionPool(nConnections: Int) {
    def get: ZIO[Any, Throwable, Connection] =
      ZIO.succeed(println("Acquired connection")) *> ZIO.succeed(Connection())
  }

  object ConnectionPool {
    def create(nConnections: Int) = new ConnectionPool(nConnections)

    def live(nConnections: Int): ZLayer[Any, Nothing, ConnectionPool] = ZLayer.succeed(create(nConnections))
  }

  case class Connection() {
    def runQuery(query: String): ZIO[Any, Throwable, Unit] =
      ZIO.succeed(println(s"Executing query: $query"))
  }

  val subscriptionService: ZIO[Any, Nothing, UserSubscription] = ZIO.succeed( // dependency injection
    UserSubscription.create(
      EmailService.create(),
      UserDatabase.create(ConnectionPool.create(10))
    )
  )

  // The dependency injection can be achieves with some factory methods
  /* Drawbacks:
  *    -> doesn't scale
  *    -> DI can ve 100x worse
  *    ->
  *
  */

  def subscribe(user: User): ZIO[Any, Throwable, Unit] = for {
    sub <- subscriptionService // service is instantiated multiple type, for every call. You risk leaking resources if you subscribe multiple users with the same effect program
    _ <- sub.subscribeUser(user)
  } yield ()

  val program: ZIO[Any, Throwable, Unit] = for {
    _ <- subscribe(User("Daniel", "daniel@rockthejvm.com"))
    _ <- subscribe(User("Bon Jovi", "jon@rockthejvm.com"))
  } yield ()

  // alternatives
  def subscribe_v2(user: User): ZIO[UserSubscription, Throwable, Unit] = for {
    sub <- ZIO.service[UserSubscription] // ZIO[UserSubscription, Nothing, UserSubscription]
    _ <- sub.subscribeUser(user)
  } yield ()

  /*
      Advantages:
        - we don't need to care about dependencies until the end of the world
        - all ZIOs requiring this dependency will use the same instance
        - Layers can be created and composed similar like ZIOs
   */

  val program_v2: ZIO[UserSubscription, Throwable, Unit] = for {
    _ <- subscribe_v2(User("Daniel", "daniel@rockthejvm.com"))
    _ <- subscribe_v2(User("Bon Jovi", "jon@rockthejvm.com"))
  } yield ()

  /*
   * ZLayers
   *
   */

  val connectionPoolLayer: ZLayer[Any, Nothing, ConnectionPool] = ZLayer.succeed(ConnectionPool.create(10)) // ZLayer which provides a connection pool  when it's invoked
  //
  val databaseLayer: ZLayer[ConnectionPool, Nothing, UserDatabase] = ZLayer.fromFunction(UserDatabase.create _)

  val emailServiceLayer: ZLayer[Any, Nothing, EmailService] =
    ZLayer.succeed(EmailService.create())

  val userSubscriptionServiceLayer: ZLayer[EmailService with UserDatabase, Nothing, UserSubscription] =
    ZLayer.fromFunction(UserSubscription.create _)

  // composing layers
  // vertical composition: >>>
  val databaseLayerFull: ZLayer[Any, Nothing, UserDatabase] = connectionPoolLayer >>> databaseLayer

  // horizontal composition: combine dependencies from both layers and the values of both layers
  val subscriptionRequirementsLayer: ZLayer[Any, Nothing, UserDatabase with EmailService] = databaseLayerFull ++ emailServiceLayer

  // mix & match
  val userSubscriptionLayer: ZLayer[Any, Nothing, UserSubscription] =
    subscriptionRequirementsLayer >>> userSubscriptionServiceLayer


  // Best practice is to create layers in the companion objects you want to expose.


  // magic
  val runnableProgram_v2 = program_v2.provide(
    UserSubscription.live,
    EmailService.live,
    UserDatabase.live,
    ConnectionPool.live(10),
    // ZIO will tell you if you are missing a layer or if you are using the same layer multiple times
    // check the graph and tell you the dependency graph.
    // ZLayer.Debug.tree
  )

  // magic v2
  val userSubscriptionLayer_v2 = ZLayer.make[UserSubscription](
    UserSubscription.live,
    EmailService.live,
    UserDatabase.live,
    ConnectionPool.live(10)
  )

  // passThrough
  private val dbWithPoolLayer:ZLayer[ConnectionPool, Nothing, ConnectionPool with UserDatabase] =
    UserDatabase.live.passthrough

  // service = take a dep and expose it as a value to further layers

  private val dbService: ZLayer[UserDatabase, Nothing, UserDatabase] = ZLayer.service[UserDatabase]
  // launch
  val subscriptionLaunch: ZIO[EmailService with UserDatabase, Nothing, Nothing] = UserSubscription.live.launch

  // memoization = once the layer is instantiated the same layer is used. This is done by default, but you need to turn it off.

  /*
     Already provided services: clock, Random, System, Console
   */

  // Automatically instantiated by import zio._
  val getTime = Clock.currentTime(TimeUnit.SECONDS)
  val randomValue = Random.nextInt
  val systemVariable = System.env("HADOOP_HOME")
  val printLineEffect = Console.printLine("This is ZIO")

  override def run: ZIO[Any, Throwable, Unit] = {
    // program

    //    program_v2.provide(ZLayer.succeed(
    //      UserSubscription.create(
    //        EmailService.create(),
    //        UserDatabase.create(ConnectionPool.create(10))
    //      )
    //    ))

    //    program_v2.provide(userSubscriptionLayer)

    runnableProgram_v2
  }
}
