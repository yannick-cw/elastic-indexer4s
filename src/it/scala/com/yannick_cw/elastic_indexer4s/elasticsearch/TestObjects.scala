package com.yannick_cw.elastic_indexer4s.elasticsearch

import com.sksamuel.elastic4s.http.{ElasticClient, ElasticNodeEndpoint}
import com.sksamuel.elastic4s.mappings.{FieldDefinition, KeywordField, ObjectField, TextField}
import com.yannick_cw.elastic_indexer4s.elasticsearch.elasic_config.{
  ElasticWriteConfig,
  MappingSetting,
  TypedMappingSetting
}
import io.circe.Json
import io.circe.parser.parse
import org.scalacheck.Gen

import scala.concurrent.duration._

object TestObjects {
  case class Address(street: String, zip: Int)
  case class User(name: String, age: Int, address: Address)
  case class NotMatchingUser(name: Int)

  private val addressGen: Gen[Address] = for {
    street <- Gen.alphaStr
    zip    <- Gen.posNum[Int]
  } yield Address(street, zip)

  private val userGen = for {
    name    <- Gen.alphaStr
    age     <- Gen.posNum[Int]
    address <- addressGen
  } yield User(name, age, address)

  def randomUser: User = userGen.sample.get

  val userMappingDef: List[FieldDefinition] = List(
    TextField("name"),
    KeywordField("age"),
    ObjectField("address").fields(
      TextField("street"),
      KeywordField("zip")
    )
  )

  def jsonSettingMapping(docType: String, shards: Int, replicas: Int): Json =
    parse(
      s"""
      |{
      |  "settings": {
      |    "number_of_shards": $shards,
      |    "number_of_replicas": $replicas
      |  },
      |  "mappings": {
      |    "$docType": {
      |      "properties" : {
      |        "address" : {
      |          "properties" : {
      |            "street" : {
      |              "type" : "text"
      |            },
      |            "zip" : {
      |              "type" : "integer"
      |            }
      |          }
      |        },
      |        "age" : {
      |          "type" : "integer"
      |        },
      |        "name" : {
      |          "type" : "text"
      |        }
      |      }
      |    }
      |  }
      |}
      |
      |
    """.stripMargin
    ).fold(throw _, identity)

  def testConf(
      mappingSetting: MappingSetting = TypedMappingSetting(),
      waitForEs: FiniteDuration = 1 second
  )(implicit c: ElasticClient): ElasticWriteConfig =
    new ElasticWriteConfig(
      elasticNodeEndpoints = List(ElasticNodeEndpoint("http", "host", 0, None)),
      cluster = "cluster",
      indexPrefix = "test_index",
      docType = "docType",
      mappingSetting = mappingSetting,
      waitForElasticTimeout = waitForEs
    ) { override lazy val client: ElasticClient = c }
}
