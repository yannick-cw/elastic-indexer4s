package com.gladow.indexer4s

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.Supervision.Decider
import akka.stream.scaladsl.Source
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}
import io.circe.Encoder

class ElasticIndexer4s(implicit system: ActorSystem, materializer: ActorMaterializer) {
  def from[A](source: Source[A, NotUsed])(implicit decoder: Encoder[A]): IndexableStream[A] = {
    new IndexableStream[A](source)
  }
}

object ElasticIndexer4s {
  def apply(system: ActorSystem, materializer: ActorMaterializer): ElasticIndexer4s =
    new ElasticIndexer4s()(system, materializer)

  def withDecider(decider: Decider) = {
    implicit val system = ActorSystem()
    implicit val materializer = ActorMaterializer()
    new ElasticIndexer4s()(system, ActorMaterializer(ActorMaterializerSettings(system).withSupervisionStrategy(decider)))
  }

  def from[Entity](source: Source[Entity, NotUsed])(implicit decoder: Encoder[Entity]): IndexableStream[Entity] = {
    implicit val system = ActorSystem()
    implicit val materializer = ActorMaterializer()
    new IndexableStream[Entity](source)
  }
}
