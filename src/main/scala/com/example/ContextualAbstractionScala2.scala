package com.example

import com.azul.crs.json.JSONSerializer

object ContextualAbstractionScala2 {

  // implicit classes.

  case class Person(name: String) {
    def greet(): String = s"Hi, my name is $name"
  }

  // implicit classes are a scala construct which automatically wrap a single value into another type which have aditional methods.

  implicit class ImpersonableString(name: String) {
    def greet(): String =
      Person(name).greet()
  }

  // extension method
  val greeting: String = "Peter".greet() // this will be new ImpersonableString("Peter).greet()

  // example: scala.concurrent.duration

  import scala.concurrent.duration._

  val oneSecond: FiniteDuration = 1.second

  // implicit arguments / value
  def increment(x: Int)(implicit amount: Int): Int = x + amount

  implicit val defaultAmount: Int = 10

  val twelve: Int = increment(2) // implicit argument 10 passed by the compiler

  def multiply(x: Int)(implicit factor: Int): Int = x * factor

  val aHundred: Int = multiply(10)

  // more complex example
  trait JSONSerializer[T] {
    def toJson(value: T): String
  }

  implicit val personSerializer: JSONSerializer[Person] = new JSONSerializer[Person] {
    override def toJson(value: Person): String = "{\"name\" : \"" + value.name + "\"}"
  }

  // Implicit defs -> Powerful
  // We want implicit values to be generated automatically. For example, if we have a serializer for person and we want to automatically generate a serializer for a List[Person]

  implicit def createListSerializer[T](implicit serializer: JSONSerializer[T]): JSONSerializer[List[T]] = {
    new JSONSerializer[List[T]] {
      override def toJson(list: List[T]): String = s"[${list.map(serializer.toJson).mkString(",")}]"
    }
  }

  def convert2Json[T](value: T)(implicit serializer: JSONSerializer[T]): String = serializer.toJson(value)

  val davidJson: String = convert2Json(Person("David")) // implicit serializer passed here

  val personsJson: String = convert2Json(List(Person("Alice"), Person("bob")))

  // implicit conversion (not recommended)
  case class Cat(name: String) {
    def meow(): String = s"$name is meowing"
  }

  implicit def string2Cat(name: String): Cat = Cat(name)
  val aCat: Cat = "Garfield" // string2Cat("Garfield")
  val garfieldMeowing = "Garfield".meow()
  // This is not recommended. Use implicit class instead.

  def main(args: Array[String]): Unit = {
    println(davidJson)
    println(personsJson)
  }
}
