package com.yannick_cw.elastic_indexer4s.elasticsearch

import akka.actor.ActorSystem
import akka.stream.scaladsl.Sink
import com.sksamuel.elastic4s.http.ElasticDsl._
import com.sksamuel.elastic4s.http.bulk.BulkResponseItem
import com.sksamuel.elastic4s.streams.ReactiveElastic._
import com.sksamuel.elastic4s.streams.{BulkIndexingSubscriber, RequestBuilder, ResponseListener}
import com.yannick_cw.elastic_indexer4s.Index_results.{IndexError, StageSucceeded, StageSuccess}
import com.yannick_cw.elastic_indexer4s.elasticsearch.elasic_config.ElasticWriteConfig

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.Try
import scala.util.control.NonFatal

class ElasticWriter[A](esConf: ElasticWriteConfig)(implicit system: ActorSystem,
                                                   requestBuilder: RequestBuilder[A],
                                                   ex: ExecutionContext) {

  import esConf._

  //promise that is passed to the error and completion function of the elastic subscriber
  private val elasticFinishPromise: Promise[Unit] = Promise[Unit]()

  private lazy val esSubscriber: BulkIndexingSubscriber[A] = client.subscriber[A](
    batchSize = writeBatchSize,
    completionFn = { () =>
      Try(elasticFinishPromise.success(())); ()
    },
    errorFn = { (t: Throwable) =>
      Try(elasticFinishPromise.failure(t)); ()
    },
    listener = new ResponseListener[A] {
      override def onAck(resp: BulkResponseItem, original: A): Unit = ()

      override def onFailure(resp: BulkResponseItem, original: A): Unit =
        //todo not yet sure if this could break too early
        Try(elasticFinishPromise.failure(new Exception("Failed indexing with: " + resp.error)))
    },
    concurrentRequests = writeConcurrentRequest,
    maxAttempts = writeMaxAttempts
  )

  lazy val esSink: Sink[A, Future[Unit]] =
    Sink
      .fromSubscriber(esSubscriber)
      .mapMaterializedValue(_ => elasticFinishPromise.future)

  private def tryIndexCreation: Try[Future[Either[IndexError, StageSucceeded]]] =
    Try(
      client
        .execute(
          mappingSetting.fold(
            typed =>
              createIndex(indexName)
                .mappings(typed.mappings)
                .analysis(typed.analyzer)
                .shards(typed.shards)
                .replicas(typed.replicas),
            unsafe =>
              createIndex(indexName)
                .source(unsafe.source.spaces2)
          )
        )
        .map(_.fold(_ => Left(IndexError("Index creation was not acknowledged")),
                    _ => Right(StageSuccess(s"Index $indexName was created")))))

  def createNewIndex: Future[Either[IndexError, StageSucceeded]] =
    Future
      .fromTry(tryIndexCreation)
      .flatten
      .recover {
        case NonFatal(t) =>
          Left(IndexError("Index creation failed.", Some(t)))
      }
}

object ElasticWriter {
  def apply[A](esConf: ElasticWriteConfig)(implicit system: ActorSystem,
                                           requestBuilder: RequestBuilder[A],
                                           ex: ExecutionContext): ElasticWriter[A] =
    new ElasticWriter[A](esConf)
}
