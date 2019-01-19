package com.yannick_cw.elastic_indexer4s

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import com.yannick_cw.elastic_indexer4s.elasticsearch.TestObjects
import com.yannick_cw.elastic_indexer4s.elasticsearch.TestObjects.{User, testConf}
import com.yannick_cw.elastic_indexer4s.specs.ItSpec
import com.sksamuel.elastic4s.Indexes
import com.sksamuel.elastic4s.http.{ElasticClient, ElasticProperties}
import com.sksamuel.elastic4s.streams.RequestBuilder
import com.yannick_cw.elastic_indexer4s.elasticsearch.elasic_config.ElasticWriteConfig
import org.scalatest.{FutureOutcome, fixture}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class ElasticIndexer4sSpec extends fixture.AsyncWordSpec with ItSpec {
  implicit val system: ActorSystem             = ActorSystem()
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val ex: ExecutionContext            = executionContext
  implicit val c: ElasticClient                = client

  // delete all indices between tests and provides fresh client, as client is stopped
  // when done running indexing
  override def withFixture(test: OneArgAsyncTest): FutureOutcome =
    complete(test(testConf()(ElasticClient(ElasticProperties(s"http://localhost:$ElasticsearchHttpPort"))))) lastly Indexes.All.values
      .foreach(deleteIndex)

  override type FixtureParam = ElasticWriteConfig

  // needed for indexable
  import com.sksamuel.elastic4s.circe._
  import io.circe.generic.auto._

  "The ElaticIndexer4s" should {
    "be able to create and write to a new index" in { baseConf =>
      val numberOfElems = 100
      val users         = Stream.continually(TestObjects.randomUser).take(numberOfElems)
      val userSource    = Source(users)

      ElasticIndexer4s(baseConf, system, materializer, executionContext)
        .from(userSource)
        .run
        .map { res =>
          blockUntilCount(numberOfElems, baseConf.indexName)
          baseConf.indexName should haveCount(numberOfElems)
          res shouldBe a[Right[_, _]]
        }
    }

    "be able to create and write to a new index with requestBuilder" in { baseConf =>
      val numberOfElems = 100
      val users         = Stream.continually(TestObjects.randomUser).take(numberOfElems)
      val userSource    = Source(users)

      implicit val builder: RequestBuilder[User] =
        (t: User) => indexInto(baseConf.indexName / baseConf.docType) source t

      ElasticIndexer4s(baseConf, system, materializer, executionContext)
        .fromBuilder(userSource)
        .run
        .map { res =>
          blockUntilCount(numberOfElems, baseConf.indexName)
          baseConf.indexName should haveCount(numberOfElems)
          res shouldBe a[Right[_, _]]
        }
    }

    "be able to write and create, afterwards if there was no other index add alias" in { baseConf =>
      val numberOfElems = 100
      val users         = Stream.continually(TestObjects.randomUser).take(numberOfElems)
      val userSource    = Source(users)
      val alias         = "alias"

      ElasticIndexer4s(baseConf, system, materializer, executionContext)
        .from(userSource)
        .switchAliasFrom(alias)
        .run
        .map { res =>
          blockUntilCount(numberOfElems, alias)
          alias should haveCount(numberOfElems)
          res shouldBe a[Right[_, _]]
          res.right.value.succeededStages.head.msg should include("was created")
          res.right.value.succeededStages.tail.tail.head.msg should include("Added alias alias to")
        }
    }

    "be able to write and switch alias afterwards if there is an index with alias" in { baseConf =>
      val numberOfElems = 100
      val users         = Stream.continually(TestObjects.randomUser).take(numberOfElems)
      val userSource    = Source(users)
      val alias         = "alias"
      Thread.sleep(1000)
      val secondConf =
        testConf(waitForEs = 2 seconds)(ElasticClient(ElasticProperties(s"http://localhost:$ElasticsearchHttpPort")))

      for {
        _ <- ElasticIndexer4s(baseConf, system, materializer, executionContext)
          .from(userSource)
          .switchAliasFrom(alias)
          .run
        res <- ElasticIndexer4s(secondConf, system, materializer, executionContext)
          .from(userSource)
          .switchAliasFrom(alias)
          .run
      } yield {
        blockUntilCount(numberOfElems, secondConf.indexName)
        secondConf.indexName should haveCount(numberOfElems)
        res shouldBe a[Right[_, _]]
        res.right.value.succeededStages.tail.tail.head.msg should include("Switched alias")
      }
    }
//
//    "be able to write, switch and then delete the old index" in { baseConf =>
//      val numberOfElems = 100
//      val users         = Stream.continually(TestObjects.randomUser).take(numberOfElems)
//      val userSource    = Source(users)
//      val alias         = "alias"
//      Thread.sleep(1000)
//      val secondConf =
//        testConf(waitForEs = 2 seconds)(ElasticClient(ElasticProperties(s"http://localhost:$ElasticsearchHttpPort")))
//
//      for {
//        _ <- ElasticIndexer4s(baseConf, system, materializer, executionContext)
//          .from(userSource)
//          .switchAliasFrom(alias)
//          .run
//        res <- ElasticIndexer4s(secondConf, system, materializer, executionContext)
//          .from(userSource)
//          .switchAliasFrom(alias)
//          .deleteOldIndices(keep = 0)
//          .run
//      } yield {
//        blockUntilIndexNotExists(baseConf.indexName)
//        blockUntilCount(numberOfElems, alias)
//        alias should haveCount(numberOfElems)
//        res shouldBe a[Right[_, _]]
//        res.right.value.succeededStages.tail.tail.head.msg should include("Switched alias")
//        res.right.value.succeededStages.tail.tail.tail.head.msg should include("Deleted indices")
//      }
//    }
  }

  override def afterAll(): Unit = {
    materializer.shutdown()
    system.terminate()
    super.afterAll()
  }
}
