package com.gladow.indexer4s.elasticsearch

import org.scalacheck.Gen

object TestObjects {
  case class Address(street: String, zip: Int)
  case class User(name: String, age: Int, address: Address)

  implicit val addressGen: Gen[Address] = for {
    street <- Gen.alphaStr
    zip <- Gen.posNum[Int]
  } yield Address(street, zip)

  val userGen = for {
    name <- Gen.alphaStr
    age <- Gen.posNum[Int]
    address <- addressGen
  } yield User(name, age, address)

  def randomUser: User = userGen.sample.get
}
