package com.gladow.indexer4s

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import cats.data.EitherT
import cats.implicits._
import com.gladow.indexer4s.IndexResults.{IndexError, RunResult, StageSucceeded, StageSuccess}
import com.gladow.indexer4s.IndexableStream.IndexAction
import com.gladow.indexer4s.elasticsearch.ElasticWriter
import com.gladow.indexer4s.elasticsearch.index_ops.{EsOpsClient, IndexDeleter, SwitchAlias}
import com.gladow.indexer4s.elasticsearch.elasic_config.ElasticWriteConfig
import io.circe.Encoder

import scala.concurrent.{ExecutionContext, Future}

object IndexableStream {
  type IndexAction = Future[Either[IndexError, RunResult]]
}

class IndexableStream[A](source: Source[A, NotUsed])
  (implicit system: ActorSystem, materializer: ActorMaterializer, encoder: Encoder[A]) {

  def runStream(esConf: ElasticWriteConfig)(implicit ex: ExecutionContext): IndexAction = {
    val esWriter = new ElasticWriter[A](esConf)
    (for {
      creationSuccess <- EitherT(esWriter.createIndexWithMapping)
      streamSuccess <- EitherT(FullStream.run(source, esWriter.esSink, esConf.logWriteSpeedEvery))
        .leftMap(_.copy(succeededStages = List(creationSuccess)))
    } yield RunResult(creationSuccess, streamSuccess))
      .value
  }

  //todo add threshold option
  def switchAliasFrom(alias: String): IndexableStreamWithSwitching =
    new IndexableStreamWithSwitching((conf, ex) => runStream(conf)(ex), alias)

  def deleteOldIndices(keep: Int, aliasProtection: Boolean = true): IndexableStreamWithDeletion =
    new IndexableStreamWithDeletion((conf, ex) => runStream(conf)(ex), keep, aliasProtection)
}

class IndexableStreamWithSwitching(run: (ElasticWriteConfig, ExecutionContext) => IndexAction, alias: String) {

  def runStream(esConf: ElasticWriteConfig)(implicit ex: ExecutionContext): IndexAction = {
    val switchAlias = new SwitchAlias(new EsOpsClient(esConf.client), alias, esConf.indexName)
    (for {
      indexResult <- EitherT(run(esConf, ex))
      switchSuccess <- EitherT(switchAlias.switchAlias)
        .leftMap(_.copy(succeededStages = indexResult.succeededStages.toList))
    } yield RunResult(indexResult.succeededStages :+ switchSuccess: _*))
      .value
  }

  def deleteOldIndices(keep: Int, aliasProtection: Boolean = true): IndexableStreamWithDeletion =
    new IndexableStreamWithDeletion((conf, ex) => runStream(conf)(ex), keep, aliasProtection)
}

class IndexableStreamWithDeletion(run: (ElasticWriteConfig, ExecutionContext) => IndexAction, keep: Int, aliasProtection: Boolean) {

  def runStream(esConf: ElasticWriteConfig)(implicit ex: ExecutionContext): IndexAction = {
    val indexDelter = new IndexDeleter(new EsOpsClient(esConf.client))
    (for {
      indexResult <- EitherT(run(esConf, ex))
      deletionSuccess <- EitherT(indexDelter.deleteOldest(esConf.esTargetIndexPrefix, esConf.indexName ,keep, aliasProtection))
        .leftMap(_.copy(succeededStages = indexResult.succeededStages.toList))
    } yield RunResult(indexResult.succeededStages :+ deletionSuccess: _*))
      .value
  }
}



