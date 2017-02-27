package com.gladow.indexer4s.elasticsearch

import akka.actor.ActorSystem
import akka.stream.scaladsl.Sink
import com.gladow.indexer4s.Index_results.{IndexError, StageSucceeded, StageSuccess}
import com.gladow.indexer4s.elasticsearch.elasic_config.ElasticWriteConfig
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.Indexable
import com.sksamuel.elastic4s.bulk.BulkCompatibleDefinition
import com.sksamuel.elastic4s.streams.ReactiveElastic._
import com.sksamuel.elastic4s.streams.{BulkIndexingSubscriber, RequestBuilder}

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.Try
import scala.util.control.NonFatal

class ElasticWriter[A](
  esConf: ElasticWriteConfig)(implicit system: ActorSystem, indexable: Indexable[A], ex: ExecutionContext) {

  import esConf._

  private implicit val builder = new RequestBuilder[A] {
    def request(post: A): BulkCompatibleDefinition =
      indexInto(indexName / esTargetType) source post
  }

  //promise that is passed to the error and completion function of the elastic subscriber
  private val elasticFinishPromise: Promise[Unit] = Promise[Unit]()

  private lazy val esSubscriber: BulkIndexingSubscriber[A] = esConf.client.subscriber[A](
    batchSize = esWriteBatchSize,
    completionFn = { () => Try(elasticFinishPromise.success(())); () },
    errorFn = { (t: Throwable) => Try(elasticFinishPromise.failure(t)); () },
    concurrentRequests = esWriteConcurrentRequest,
    maxAttempts = esWriteMaxAttempts
  )

  val esSink: Sink[A, Future[Unit]] =
    Sink.fromSubscriber(esSubscriber)
      .mapMaterializedValue(_ => elasticFinishPromise.future)

  private def tryIndexCreation: Future[Either[IndexError, StageSucceeded]] = esConf.client.execute(
      createIndex(indexName).mappings(esTargetMappings).analysis(esTargetAnalyzers)/*.shards(indexShards).replicas(indexReplicas)*/
  ).map(res =>
      if (res.isAcknowledged) Right(StageSuccess(s"Index $indexName was created"))
      else Left(IndexError("Index creation was not acknowledged"))
  )

  def createIndexWithMapping: Future[Either[IndexError, StageSucceeded]] = tryIndexCreation.
    recover { case NonFatal(t) =>
      Left(IndexError("Index creation failed with: " + t.getStackTrace.mkString("\n")))
    }
}

object ElasticWriter {
  def apply[A](esConf: ElasticWriteConfig)
    (implicit system: ActorSystem, indexable: Indexable[A], ex: ExecutionContext): ElasticWriter[A] =
    new ElasticWriter[A](esConf)
}