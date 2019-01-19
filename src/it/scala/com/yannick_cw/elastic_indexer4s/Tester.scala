package com.yannick_cw.elastic_indexer4s

import akka.stream.scaladsl.Source
import com.sksamuel.elastic4s.Indexable
import com.sksamuel.elastic4s.http.ElasticNodeEndpoint
import com.whisk.docker.impl.spotify.DockerKitSpotify
import com.whisk.docker.scalatest.DockerTestKit
import com.yannick_cw.elastic_indexer4s.Index_results.{IndexError, RunResult}
import com.yannick_cw.elastic_indexer4s.elasticsearch.elasic_config.ElasticWriteConfig
import io.circe.generic.auto._
import io.circe.syntax._
import org.scalatest.{AsyncFlatSpec, Matchers}

import scala.concurrent.{ExecutionContext, Future}

class Tester
    extends AsyncFlatSpec
    with IndexerElasticSearchService
    with DockerTestKit
    with DockerKitSpotify
    with Matchers {

  implicit val ec: ExecutionContext = executionContext

  it should "index" in {

    val config = ElasticWriteConfig(
      esNodeEndpoints = List(ElasticNodeEndpoint("http", "localhost", ElasticsearchHttpPort, None)),
      esTargetCluster = "elasticsearch",
      esTargetIndexPrefix = "test_index",
      esTargetType = "documents"
    )

    case class Document(s: String)
    object Document {
      implicit val indexable: Indexable[Document] = (t: Document) => t.asJson.noSpaces
    }

    val dummySource = Source.single(Document("testDoc"))

    val runResult: Future[Either[IndexError, RunResult]] = ElasticIndexer4s(config)
      .from(dummySource)
      .switchAliasFrom(alias = "alias")
      .deleteOldIndices(keep = 2)
      .run

    runResult.map(_.right.get.succeededStages shouldNot be(empty))
  }

}
