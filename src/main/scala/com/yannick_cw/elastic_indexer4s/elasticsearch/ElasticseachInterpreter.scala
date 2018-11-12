package com.yannick_cw.elastic_indexer4s.elasticsearch

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import cats.data.EitherT
import com.sksamuel.elastic4s.streams.RequestBuilder
import com.yannick_cw.elastic_indexer4s.Index_results.{IndexError, StageSucceeded}
import com.yannick_cw.elastic_indexer4s.elasticsearch.elasic_config.ElasticWriteConfig
import com.yannick_cw.elastic_indexer4s.elasticsearch.index_ops.{AliasSwitching, EsOpsClient, IndexDeletion}
import com.yannick_cw.elastic_indexer4s.indexing_logic.FullStream

import scala.concurrent.{ExecutionContext, Future}

trait EsAccess[F[_]] {
  def createIndex(): EitherT[F, IndexError, StageSucceeded]
  def indexSource[A: RequestBuilder](source: Source[A, NotUsed]): EitherT[F, IndexError, StageSucceeded]
  def switchAlias(minT: Double, maxT: Double, alias: String): EitherT[F, IndexError, StageSucceeded]
  def deleteOldIndices(keep: Int, aliasProtection: Boolean): EitherT[F, IndexError, StageSucceeded]
}

class ElasticseachInterpreter(esConf: ElasticWriteConfig)(implicit ex: ExecutionContext,
                                                          system: ActorSystem,
                                                          materializer: ActorMaterializer)
    extends EsAccess[Future] {

  private val writer      = ElasticWriter(esConf)
  private val esOpsClient = EsOpsClient(esConf.client)

  def createIndex(): EitherT[Future, IndexError, StageSucceeded] = EitherT(writer.createNewIndex)

  def indexSource[A: RequestBuilder](source: Source[A, NotUsed]): EitherT[Future, IndexError, StageSucceeded] =
    EitherT(FullStream.run(source, writer.esSink, esConf.logWriteSpeedEvery))
  def switchAlias(minT: Double, maxT: Double, alias: String): EitherT[Future, IndexError, StageSucceeded] =
    EitherT(
      AliasSwitching(esOpsClient, minT, maxT, esConf.waitForElasticTimeout.toMillis)
        .switchAlias(alias, esConf.indexName))

  def deleteOldIndices(keep: Int, aliasProtection: Boolean): EitherT[Future, IndexError, StageSucceeded] =
    EitherT(
      IndexDeletion(esOpsClient)
        .deleteOldest(esConf.indexPrefix, esConf.indexName, keep, aliasProtection))
}
