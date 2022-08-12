package com.example

import java.util

object Variance {

  // OOP -> relay on substitution

  class Animal

  class Dog(name: String) extends Animal

  // regular java object substitution
  val lassie = new Dog("Lassie")
  val animal: Animal = lassie

  // Variance question for List: if Dog <: Animal, then should List[Dog] <: List[Animal] ?

  // YES -> COVARIANT
  val hachi = new Dog("Hachi")
  val laika = new Dog("Laika")
  val someAnimals: List[Animal] = List(hachi, laika)

  // This class is COVARIANT
  class MyList[+A]

  val myAnimalList: MyList[Animal] = new MyList[Dog]

  // NO -> the type is INVARIANT
  trait Semigroup[A] {
    def combine(x: A, y: A): A
  }

  // example of invariant are all generics in Java
  // this doesn't compile
  // val aJavaList: java.util.ArrayList[Animal] = new util.ArrayList[Dog]()

  // Hell NO -> CONTRAVARIANT -> inverse of Contravariant
  trait Vet[-A] {
    def heal(animal: A): Boolean
  }

  // A vet of Animal is in some respects better than a Vet[Dog]
  val myVet: Vet[Dog] = new Vet[Animal] {
    override def heal(animal: Animal): Boolean = true
  }

  // Because Dog <: Anima, then Vet[Dog] >: Vet[Animal]

  // this is allowed.
  def healingLassie = myVet.heal(lassie)

  /**
   * How do we peek contravariant, contravariant, invariant generic types?
   *
   * Rule of thumb:
   * If the type PRODUCES or RETRIES values of type A (e.g. lists), then the type should be COVARIANT
   * If the type CONSUMES or ACTS ON values of types A (e.g. a vet) then the type should be CONTRAVARIANT
   * Otherwise, INVARIANT
   */

  /**
   * Variance positions
   *
   */

  /*  class Vet2[-A](val favoriteAnimal: A) // DOESN'T COMPILE  the types of val fields are in COVARIANT position
      var garfield = new Cat
      val theVet: Vet2[Animal] = new Vet2[Animal](garfield)
      val dogVet: Vet2[Dog] = theVet
      val favoriteDog = dogVet.favoriteAnimal  // must be a Dog type - type conflict
  */

  /*
      class MutableContainer[+A](var content: A)  // the type of var fields are in a CONTRAVARIANT position.
      val containerAnimal: MutableContainer[Animal] = new MutableContainer[Dog](new Dog)
      containerAnimal.content = new Cat  // type conflict
   */

  // Variable fields are compatible with INVARIANT type

  // types of method arguments are in Contravariant position

  /*
      class MyList2[+A] {
        def add(element: A): MyList[A]
      }

      val animals: MyList2[Animal] = new MyList2[Cat]
      val biggerListOfAnimals: MyList2[Animal] = animals.add(new Dog) // type conflict
   */

  // Solution:
  class MyList2[+A] {
    def add[B >: A](element: B): MyList[B] = ???
  }
  // if I have a list of Cats and if I add a Dog into the list, that will result in a list of Animals, because Animal is the first type which correctly describe both types

  /*
    class Vet2[-A] {
      def rescueAnima(): A = ???
    }

    val vet: Vet2[Animal] = new Vet2[Animal] {
       def rescueAnimal(): Animal = new Cat
    }

    val lassieVet: Vet2[Dog] = vet
    val rescueDog = lassieVet.rescueAnimal() // must return a Dog, but returns a Cat -> type conflict

   */

  // Solution:
  class Vet2[-A] {
    def rescueAnima[B <: A](): B = ???
  }


  def main(args: Array[String]): Unit = {

  }
}
