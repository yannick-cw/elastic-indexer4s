package com.yannick_cw.indexer4s

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.Supervision.Decider
import akka.stream.scaladsl.Source
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}
import com.yannick_cw.indexer4s.elasticsearch.elasic_config.ElasticWriteConfig
import com.sksamuel.elastic4s.Indexable
import cats.instances.future.catsStdInstancesForFuture
import com.yannick_cw.indexer4s.elasticsearch.ElasticseachInterpreter
import com.yannick_cw.indexer4s.indexing_logic.IndexableStream

import scala.concurrent.{ExecutionContext, Future}

class ElasticIndexer4s(esConf: ElasticWriteConfig)(implicit system: ActorSystem, materializer: ActorMaterializer, ex: ExecutionContext) {
  def from[A](source: Source[A, NotUsed])(implicit indexable: Indexable[A]): IndexableStream[A, Future] =
    new IndexableStream[A, Future](source, new ElasticseachInterpreter[A](esConf))(catsStdInstancesForFuture(ex))

  def withDecider(decider: Decider): ElasticIndexer4s = {
    val materializer = ActorMaterializer(ActorMaterializerSettings(system).withSupervisionStrategy(decider))
    new ElasticIndexer4s(esConf)(system, materializer, ex)
  }
}

object ElasticIndexer4s {
  def apply(esConf: ElasticWriteConfig, system: ActorSystem, materializer: ActorMaterializer, ex: ExecutionContext): ElasticIndexer4s =
    new ElasticIndexer4s(esConf)(system, materializer, ex)

  def apply(esConf: ElasticWriteConfig) = {
    implicit val system = ActorSystem()
    implicit val ex = system.dispatcher
    implicit val materializer = ActorMaterializer()
    new ElasticIndexer4s(esConf)
  }
}
