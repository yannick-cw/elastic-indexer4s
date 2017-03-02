package com.gladow.indexer4s.elasticsearch

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import cats.~>
import com.gladow.indexer4s.elasticsearch.elasic_config.ElasticWriteConfig
import com.gladow.indexer4s.elasticsearch.index_ops.{AliasSwitching, EsOpsClient, IndexDeletion}
import com.gladow.indexer4s.indexing_logic.FullStream
import com.gladow.indexer4s.indexing_logic.IndexLogic._
import com.sksamuel.elastic4s.Indexable

import scala.concurrent.{ExecutionContext, Future}

class ElasticseachInterpreter[Entity](esConf: ElasticWriteConfig)
  (implicit ex: ExecutionContext, system: ActorSystem, materializer: ActorMaterializer, indexable: Indexable[Entity])
  extends (IndexAction ~> Future) {

  private val writer = ElasticWriter[Entity](esConf)
  private val esOpsClient = EsOpsClient(esConf.client)

  override def apply[A](fa: IndexAction[A]): Future[A] = fa match {
    case CreateIndex =>
      writer.createNewIndex

    case IndexSource(source: Source[Entity, NotUsed]) =>
      FullStream.run(source, writer.esSink, esConf.logWriteSpeedEvery)

    case SwitchAlias(minT, maxT, alias) =>
      AliasSwitching(esOpsClient, minT, maxT, esConf.waitForElasticTimeout.toMillis).switchAlias(alias, esConf.indexName)

    case DeleteOldIndices(keep, aliasProtection) =>
      IndexDeletion(esOpsClient).deleteOldest(esConf.indexPrefix, esConf.indexName, keep, aliasProtection)
  }
}
