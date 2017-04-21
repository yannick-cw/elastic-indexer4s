package com.yannick_cw.elastic_indexer4s.elasticsearch

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Keep, Source}
import com.yannick_cw.elastic_indexer4s.Index_results.{IndexError, StageSucceeded}
import com.yannick_cw.elastic_indexer4s.elasticsearch.TestObjects.{User, _}
import com.yannick_cw.elastic_indexer4s.elasticsearch.elasic_config.ElasticWriteConfig
import com.yannick_cw.elastic_indexer4s.specs.ItSpec
import com.sksamuel.elastic4s.Indexes
import com.sksamuel.elastic4s.circe._
import com.sksamuel.elastic4s.mappings.{MappingContentBuilder, MappingDefinition}
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.{Encoder, Json, JsonObject}
import org.scalatest.FutureOutcome

import scala.collection.JavaConverters._

class ElasticWriterSpec extends ItSpec {
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

  // delete all indices between tests
  override def withFixture(test: NoArgAsyncTest): FutureOutcome =
    complete(test()) lastly Indexes.All.values.foreach(deleteIndex)

  val baseConf = testConf()

  "ElasticWriter" should {
    "be able to create a new index" in {
      ElasticWriter(baseConf).createNewIndex
        .map(_.right.value shouldBe a[StageSucceeded])
    }

    "be able to create the index with shards and replicas" in {
      val replicas = 5
      val shards = 3
      val conf = testConf(Some(replicas), Some(shards))
      for {
        _ <- ElasticWriter(conf).createNewIndex
        settings <- client.execute(getSettings(conf.indexName))
      } yield {
        settings.getSetting(conf.indexName, "index.number_of_shards").toInt shouldBe shards
        settings.getSetting(conf.indexName, "index.number_of_replicas").toInt shouldBe replicas
      }
    }

    "be able to create the index and write elements" in {
      val writer = ElasticWriter[User](baseConf)
      val numberOfElems = 1000
      val users = Stream.continually(TestObjects.randomUser).take(numberOfElems)
      val userSource = Source(users)
      for {
        _ <- writer.createNewIndex
        _ <- userSource.toMat(writer.esSink)(Keep.right).run
        _ = blockUntilCount(numberOfElems, baseConf.indexName)
        allUsers <- client.execute(search(baseConf.indexName) matchAllQuery() size numberOfElems)
      } yield {
        baseConf.indexName should haveCount(numberOfElems)
        allUsers.to[User] should contain theSameElementsAs users
      }
    }

    "be able to create the index with a mapping" in {
      val userMapping = MappingDefinition(baseConf.docType) fields userMappingDef
      val conf = testConf(mappings = List(userMapping))
      val writer = ElasticWriter[User](conf)
      for {
        _ <- writer.createNewIndex.map(_.fold(er => throw new IllegalArgumentException(er.msg), identity))
        _ = blockUntilIndexExists(conf.indexName)
        mapping <- client.execute(getMapping(conf.indexName))
      } yield {
        val realMapping = esMappingToJson(mapping.mappings(conf.indexName)(conf.docType).getSourceAsMap)
        val expected = parse(MappingContentBuilder.build(userMapping).string.replaceAll(""""type":"object",""", ""))
          .fold(throw _, identity)

        realMapping should be(expected)
      }
    }

    "be able to create the index with a mapping and not change the mapping after writing elements" in {
      val userMapping = MappingDefinition(baseConf.docType) fields userMappingDef
      val conf = testConf(mappings = List(userMapping))
      val writer = ElasticWriter[User](conf)
      val numberOfElems = 10
      val users = Stream.continually(TestObjects.randomUser).take(numberOfElems)
      val userSource = Source(users)
      for {
        _ <- writer.createNewIndex.map(_.fold(er => throw new IllegalArgumentException(er.msg), identity))
        _ <- userSource.toMat(writer.esSink)(Keep.right).run
        _ = blockUntilCount(numberOfElems, conf.indexName)
        mapping <- client.execute(getMapping(conf.indexName))
      } yield {
        val realMapping = esMappingToJson(mapping.mappings(conf.indexName)(conf.docType).getSourceAsMap)
        val expected = parse(MappingContentBuilder.build(userMapping).string.replaceAll(""""type":"object",""", ""))
          .fold(throw _, identity)

        realMapping should be(expected)
      }
    }

    "fail with index creation, if no connection could be established" in {
      val notWorkingEs = ElasticWriteConfig(List("host"), 999, "cluster", "prefix", "docsType")
      val writer = ElasticWriter[User](notWorkingEs)
      writer.createNewIndex
        .map(_.left.value shouldBe an[IndexError])
    }
  }

  def esMappingToJson(value: AnyRef): Json = value match {
    case map: java.util.LinkedHashMap[String, AnyRef] =>
      Encoder.encodeJsonObject(JsonObject.fromIterable(map.asScala.mapValues(esMappingToJson)))
    case str: String =>
      Encoder.encodeString(str)
  }

  override def afterAll(): Unit = {
    materializer.shutdown()
    system.terminate()
    super.afterAll()
  }
}
