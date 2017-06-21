package com.yannick_cw.elastic_indexer4s

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.Supervision.Decider
import akka.stream.scaladsl.Source
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}
import com.yannick_cw.elastic_indexer4s.elasticsearch.elasic_config.ElasticWriteConfig
import com.sksamuel.elastic4s.Indexable
import cats.instances.future.catsStdInstancesForFuture
import com.sksamuel.elastic4s.streams.RequestBuilder
import com.yannick_cw.elastic_indexer4s.elasticsearch.ElasticseachInterpreter
import com.yannick_cw.elastic_indexer4s.indexing_logic.IndexableStream
import com.sksamuel.elastic4s.ElasticDsl._

import scala.concurrent.{ExecutionContext, Future}

class ElasticIndexer4s(esConf: ElasticWriteConfig)(implicit system: ActorSystem,
                                                   materializer: ActorMaterializer,
                                                   ex: ExecutionContext) {

  /**
    * Creates a IndexableStream from a Source of elements and an Indexable for the element type
    */
  def from[Entity](source: Source[Entity, NotUsed])(
      implicit indexable: Indexable[Entity]): IndexableStream[Entity, Future] = {
    implicit val defaultBuilder: RequestBuilder[Entity] =
      (entity: Entity) => indexInto(esConf.indexName / esConf.docType) source entity

    new IndexableStream[Entity, Future](source, new ElasticseachInterpreter[Entity](esConf))(
      catsStdInstancesForFuture(ex))
  }

  /**
    * Creates a IndexableStream from a Source of elements and an requestBuilder for the element type
    * This can be used for additional configuration on how to index elements
    */
  def fromBuilder[Entity](source: Source[Entity, NotUsed])(
      implicit requestBuilder: RequestBuilder[Entity]): IndexableStream[Entity, Future] =
    new IndexableStream[Entity, Future](source, new ElasticseachInterpreter[Entity](esConf))(
      catsStdInstancesForFuture(ex))

  def withDecider(decider: Decider): ElasticIndexer4s = {
    val materializer = ActorMaterializer(
      ActorMaterializerSettings(system).withSupervisionStrategy(decider))
    new ElasticIndexer4s(esConf)(system, materializer, ex)
  }
}

object ElasticIndexer4s {
  def apply(esConf: ElasticWriteConfig,
            system: ActorSystem,
            materializer: ActorMaterializer,
            ex: ExecutionContext): ElasticIndexer4s =
    new ElasticIndexer4s(esConf)(system, materializer, ex)

  def apply(esConf: ElasticWriteConfig): ElasticIndexer4s = {
    implicit val system = ActorSystem()
    implicit val ex = system.dispatcher
    implicit val materializer = ActorMaterializer()
    new ElasticIndexer4s(esConf)
  }
}
