package com.gladow.indexer4s.elasticsearch

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Keep, Sink, Source}
import com.gladow.indexer4s.Index_results.StageSucceeded
import com.gladow.indexer4s.elasticsearch.TestObjects.User
import com.gladow.indexer4s.elasticsearch.elasic_config.ElasticWriteConfig
import com.gladow.indexer4s.specs.ItSpec
import com.sksamuel.elastic4s.{Indexes, TcpClient}
import org.scalatest.FutureOutcome
import io.circe.generic.auto._
import com.sksamuel.elastic4s.circe._

class ElasticWriterSpec extends ItSpec { self =>
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

  // delete all indices between tests
  override def withFixture(test: NoArgAsyncTest): FutureOutcome =
    complete(test()) lastly Indexes.All.values.foreach(deleteIndex)

  val testConf = new ElasticWriteConfig(
    esTargetHosts = "host" :: Nil,
    esTargetPort = 0,
    esTargetCluster = "cluster",
    esTargetIndexPrefix = "test_index",
    esTargetType = "docs"
  ) { override lazy val client: TcpClient = self.client }

  "ElasticWriter" should {
    "be able to create a new index" in {
      ElasticWriter(testConf).createNewIndex
        .map(_.right.value shouldBe a[StageSucceeded])
    }

    "be able to create the index and write elements" in {
      val writer = ElasticWriter[User](testConf)
      val numberOfElems = 1000
      val users = Stream.continually(TestObjects.randomUser).take(numberOfElems)
      val userSource = Source(users)
      for {
        _ <- writer.createNewIndex
        _ <- userSource.toMat(writer.esSink)(Keep.right).run
        _ = blockUntilCount(numberOfElems, testConf.indexName)
        allUsers <- client.execute(search(testConf.indexName) matchAllQuery() size numberOfElems)
      } yield {
        testConf.indexName should haveCount(numberOfElems)
        allUsers.to[User] should contain theSameElementsAs users
      }
    }
  }

  override def afterAll(): Unit = {
    materializer.shutdown()
    system.terminate()
    super.afterAll()
  }
}
