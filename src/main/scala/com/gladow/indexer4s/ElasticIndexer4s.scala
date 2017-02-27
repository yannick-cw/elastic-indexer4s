package com.gladow.indexer4s

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.Supervision.Decider
import akka.stream.scaladsl.Source
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}
import com.sksamuel.elastic4s.Indexable

class ElasticIndexer4s(implicit system: ActorSystem, materializer: ActorMaterializer) {
  def from[A](source: Source[A, NotUsed])(implicit indexable: Indexable[A]): IndexableStream[A] = {
    new IndexableStream[A](source)
  }
}

object ElasticIndexer4s {
  def apply(system: ActorSystem, materializer: ActorMaterializer): ElasticIndexer4s =
    new ElasticIndexer4s()(system, materializer)

  def withDecider(decider: Decider): ElasticIndexer4s = {
    implicit val system = ActorSystem()
    implicit val materializer = ActorMaterializer()
    new ElasticIndexer4s()(system, ActorMaterializer(ActorMaterializerSettings(system).withSupervisionStrategy(decider)))
  }

  def from[A](source: Source[A, NotUsed])(implicit indexable: Indexable[A]): IndexableStream[A] = {
    implicit val system = ActorSystem()
    implicit val materializer = ActorMaterializer()
    new IndexableStream[A](source)
  }
}
