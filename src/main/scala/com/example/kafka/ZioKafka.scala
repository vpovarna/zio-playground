package com.example.kafka

import org.apache.kafka.clients.producer.{ProducerRecord, RecordMetadata}
import zio._
import zio.json._
import zio.kafka.consumer._
import zio.kafka.producer._
import zio.kafka.serde.Serde
import zio.stream.ZSink

object Domain {
  // create encoders and decoders for both classes
  case class MatchPlayer(name: String, score: Int) {
    override def toString: String = s"$name: $score"
  }

  object MatchPlayer {
    implicit val encoder: JsonEncoder[MatchPlayer] = DeriveJsonEncoder.gen[MatchPlayer]
    implicit val decoder: JsonDecoder[MatchPlayer] = DeriveJsonDecoder.gen[MatchPlayer]
  }

  case class Match(players: Array[MatchPlayer]) {
    def score: String = s"${players(0)} - ${players(1)} "
  }

  object Match {
    implicit val encoder: JsonEncoder[Match] = DeriveJsonEncoder.gen[Match]
    implicit val decoder: JsonDecoder[Match] = DeriveJsonDecoder.gen[Match]
  }

  // defining match serdes
  val matchSerde: Serde[Any, Match] = Serde.string.inmapM { str =>
    // deserialization
    val value = str.fromJson[Match]
    ZIO.fromEither(value.left.map(errorMessage => new RuntimeException(errorMessage)))
  } { theMatch =>
    // serialization
    ZIO.attempt(theMatch.toJson)
  }
}

object ZioKafka extends ZIOAppDefault {

  val consumerSettings: ConsumerSettings = ConsumerSettings(List("localhost:9092"))
    .withGroupId("group")
    .withCloseTimeout(30.seconds)

  val consumerManaged: ZIO[Scope, Throwable, Consumer] = Consumer.make(consumerSettings)

  val consumer = ZLayer.scoped(consumerManaged)

  import Domain._

  val footballMatchesStream = Consumer.subscribeAnd(Subscription.topics("updates"))
    .plainStream(Serde.string, Serde.string)

  val matchesStream = Consumer.subscribeAnd(Subscription.topics("updates"))
    .plainStream(Serde.string, matchSerde)

  val matchesPrintableStream =
    matchesStream
      .map(cr => (cr.value.score, cr.offset))
      .tap {
        case (score, _) => Console.printLine(s" | $score |") // will be perform for every value inside the screen
      }
      .map(_._2) // keeping only the offset for commit
      .aggregateAsync(Consumer.offsetBatches) // relay on ZIO fiber. For every chunk we'll store the maximum offset and we'll upload the maximum value to the kafka, so that the consumer knows what to read.
  //      .mapZIO(_.commit)
  //      .runDrain

  val streamEffect = matchesPrintableStream.run(ZSink.foreach(offset => offset.commit))

  override def run: ZIO[Any with ZIOAppArgs with Scope, Any, Any] =
    streamEffect.provideSomeLayer(consumer)

}

object ZioKafkaProducer extends ZIOAppDefault {

  import Domain._

  val producerSettings: ProducerSettings = ProducerSettings(List("localhost:9092"))

  val producerStream: ZIO[Scope, Throwable, Producer] = Producer.make(producerSettings)
  val producer: ZLayer[Any, Throwable, Producer] = ZLayer.scoped(producerStream)

  val finalScore: Match = Match(Array(MatchPlayer("ITA", 1), MatchPlayer("ENG", 2)))
  val record = new ProducerRecord[String, Match](
    "updates",
    "updates-3",
    finalScore
  )

  val producerEffect: RIO[Any with Producer, RecordMetadata] = Producer.produce(record, Serde.string, matchSerde)

  override def run: ZIO[Any with ZIOAppArgs with Scope, Any, Any] = producerEffect.provideSomeLayer(producer)
}
