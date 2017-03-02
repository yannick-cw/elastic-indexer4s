package com.yannick_cw.indexer4s.elasticsearch

import com.yannick_cw.indexer4s.elasticsearch.elasic_config.ElasticWriteConfig
import com.sksamuel.elastic4s.ElasticDsl.field
import com.sksamuel.elastic4s.TcpClient
import com.sksamuel.elastic4s.mappings.FieldType._
import com.sksamuel.elastic4s.mappings.{MappingDefinition, TypedFieldDefinition}
import org.scalacheck.Gen
import scala.concurrent.duration._

object TestObjects {
  case class Address(street: String, zip: Int)
  case class User(name: String, age: Int, address: Address)
  case class NotMatchingUser(name: Int)

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

  val userMappingDef: List[TypedFieldDefinition] = List(
    field("name", TextType),
    field("age", IntegerType),
    field("address", ObjectType) inner(
      field("street", TextType),
      field("zip", IntegerType)
    )
  )

  def testConf(
    replicas: Option[Int] = None,
    shards: Option[Int] = None,
    mappings: List[MappingDefinition] = List.empty,
    waitForEs: FiniteDuration = 1 second
  )(implicit c: TcpClient) = new ElasticWriteConfig(
    hosts = "host" :: Nil,
    port = 0,
    cluster = "cluster",
    indexPrefix = "test_index",
    docType = "docs",
    shards = shards,
    replicas = replicas,
    mappings = mappings,
    waitForElasticTimeout = waitForEs
  ) { override lazy val client: TcpClient = c }
}
