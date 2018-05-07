package com.yannick_cw.elastic_indexer4s.elasticsearch

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Keep, Source}
import com.sksamuel.elastic4s.Indexes
import com.sksamuel.elastic4s.circe._
import com.sksamuel.elastic4s.mappings.{MappingBuilderFn, MappingDefinition}
import com.sksamuel.elastic4s.http.ElasticDsl._
import com.sksamuel.elastic4s.streams.RequestBuilder
import com.yannick_cw.elastic_indexer4s.Index_results.{IndexError, StageSucceeded}
import com.yannick_cw.elastic_indexer4s.elasticsearch.TestObjects.{User, _}
import com.yannick_cw.elastic_indexer4s.elasticsearch.elasic_config.{ElasticWriteConfig, StringMappingSetting, TypedMappingSetting}
import com.yannick_cw.elastic_indexer4s.specs.ItSpec
import io.circe.generic.auto._
import io.circe.optics.JsonPath._
import io.circe.parser._
import io.circe.{Encoder, Json, JsonObject}
import org.scalatest.FutureOutcome

class ElasticWriterSpec extends ItSpec {
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

  // delete all indices between tests
  override def withFixture(test: NoArgAsyncTest): FutureOutcome =
    complete(test()) lastly Indexes.All.values.foreach(deleteIndex)

  val baseConf = testConf()

  def userRequestBuilder(indexName: String): RequestBuilder[User] =
    (t: User) => indexInto(indexName / testConf().docType) source t

  "ElasticWriter" should {
    "be able to create a new index" in {
      implicit val builder = userRequestBuilder(baseConf.indexName)

      ElasticWriter(baseConf).createNewIndex
        .map(_.right.value shouldBe a[StageSucceeded])
    }

    "be able to create the index with shards and replicas" in {
      val replicas = 5
      val shards = 3
      val conf = testConf(TypedMappingSetting(replicas = Some(replicas), shards = Some(shards)))
      implicit val builder = userRequestBuilder(conf.indexName)

      for {
        _ <- ElasticWriter(conf).createNewIndex
        settings <- http.execute(getSettings(conf.indexName)).toResult
      } yield {
        settings.settings(conf.indexName)("index.number_of_shards").toInt shouldBe shards
        settings.settings(conf.indexName)("index.number_of_replicas").toInt shouldBe replicas
      }
    }

    "be able to create the index and write elements" in {
      implicit val builder = userRequestBuilder(baseConf.indexName)
      val writer = ElasticWriter[User](baseConf)
      val numberOfElems = 1000
      val users = Stream.continually(TestObjects.randomUser).take(numberOfElems)
      val userSource = Source(users)
      for {
        _ <- writer.createNewIndex
        _ <- userSource.toMat(writer.esSink)(Keep.right).run
        _ = blockUntilCount(numberOfElems, baseConf.indexName)
        allUsers <- http.execute(search(baseConf.indexName) matchAllQuery() size numberOfElems).toResult
      } yield {
        baseConf.indexName should haveCount(numberOfElems)
        allUsers.to[User] should contain theSameElementsAs users
      }
    }

    "be able to create the index with a MappingDefinition" in {
      implicit val builder = userRequestBuilder(baseConf.indexName)
      val userMapping = MappingDefinition(baseConf.docType) fields userMappingDef
      val conf = testConf(TypedMappingSetting(mappings = List(userMapping)))
      val writer = ElasticWriter[User](conf)
      for {
        _ <- writer.createNewIndex.map(_.fold(er => throw new IllegalArgumentException(er.msg), identity))
        _ = blockUntilIndexExists(conf.indexName)
        mappings <- http.execute(getMapping(conf.indexName)).toResult
      } yield {
        val realMapping = esMappingToJson(mappings.find(_.index == conf.indexName).get.mappings(conf.docType))
        val expected = parse(MappingBuilderFn.build(userMapping).string.replaceAll(""""type":"object",""", ""))
          .map(_.hcursor.downField("properties").focus.get)
          .fold(throw _, identity)

        realMapping should be(expected)
      }
    }

    "be able to create the index with a mapping and not change the mapping after writing elements" in {
      val userMapping = MappingDefinition(baseConf.docType) fields userMappingDef
      val conf = testConf(TypedMappingSetting(mappings = List(userMapping)))
      implicit val builder = userRequestBuilder(conf.indexName)
      val writer = ElasticWriter[User](conf)
      val numberOfElems = 10
      val users = Stream.continually(TestObjects.randomUser).take(numberOfElems)
      val userSource = Source(users)
      for {
        _ <- writer.createNewIndex.map(_.fold(er => throw new IllegalArgumentException(er.msg), identity))
        _ <- userSource.toMat(writer.esSink)(Keep.right).run
        _ = blockUntilCount(numberOfElems, conf.indexName)
        mappings <- http.execute(getMapping(conf.indexName)).toResult
      } yield {
        val realMapping = esMappingToJson(mappings.find(_.index == conf.indexName).get.mappings(conf.docType))
        val expected = parse(MappingBuilderFn.build(userMapping).string.replaceAll(""""type":"object",""", ""))
          .map(_.hcursor.downField("properties").focus.get)
          .fold(throw _, identity)

        realMapping should be(expected)
      }
    }

    "be able to create an index with string mapping and settings and not change the mapping after writing elements" in {
      val replicas = 9
      val shards = 7
      val mappingSettingJson = jsonSettingMapping("docType", shards, replicas)
      val unsafeSettingMapping = StringMappingSetting
        .unsafeString(mappingSettingJson.spaces2).fold(throw _, identity)
      val conf = testConf(mappingSetting = unsafeSettingMapping)

      implicit val builder = userRequestBuilder(conf.indexName)
      val writer = ElasticWriter[User](conf)
      val numberOfElems = 10
      val users = Stream.continually(TestObjects.randomUser).take(numberOfElems)
      val userSource = Source(users)
      for {
        _ <- writer.createNewIndex.map(_.fold(er => throw new IllegalArgumentException(er.msg), identity))
        _ <- userSource.toMat(writer.esSink)(Keep.right).run
        _ = blockUntilCount(numberOfElems, conf.indexName)
        mappings <- http.execute(getMapping(conf.indexName)).toResult
        settings <- http.execute(getSettings(conf.indexName)).toResult
      } yield {
        val realMapping = esMappingToJson(mappings.find(_.index == conf.indexName).get.mappings(conf.docType))
        settings.settings(conf.indexName)("index.number_of_shards").toInt shouldBe shards
        settings.settings(conf.indexName)("index.number_of_replicas").toInt shouldBe replicas
        realMapping should be(root.mappings.docType.properties.json.getOption(mappingSettingJson).get)
      }
    }

    "fail with index creation, if no connection could be established" in {
      val notWorkingEs = ElasticWriteConfig(List("host"), 999, "cluster", "prefix", "docsType")
      implicit val builder = userRequestBuilder(notWorkingEs.indexName)
      val writer = ElasticWriter[User](notWorkingEs)
      writer.createNewIndex
        .map(_.left.value shouldBe an[IndexError])
    }
  }

  def esMappingToJson(value: AnyRef): Json = value match {
    case map: Map[String, AnyRef] =>
      Encoder.encodeJsonObject(JsonObject.fromIterable(map.mapValues(esMappingToJson)))
    case str: String =>
      Encoder.encodeString(str)
  }

  override def afterAll(): Unit = {
    materializer.shutdown()
    system.terminate()
    super.afterAll()
  }
}
