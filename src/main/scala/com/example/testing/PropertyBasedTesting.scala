package com.example.testing

import zio._
import zio.test._

object PropertyBasedTesting extends ZIOSpecDefault {

  override def spec =
    test("property-based-testing basic") {
      check(Gen.int, Gen.int, Gen.int) { (x, y, z) =>
        assertTrue(((x + y) + z) == (x + (y + z)))
      }
    }
}
