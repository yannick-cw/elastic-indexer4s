package com.gladow.indexer4s

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import cats.data.EitherT
import cats.implicits._
import com.gladow.indexer4s.Index_results.{IndexError, RunResult, StageSucceeded}
import com.gladow.indexer4s.IndexableStream.IndexAction
import com.gladow.indexer4s.elasticsearch.ElasticWriter
import com.gladow.indexer4s.elasticsearch.elasic_config.ElasticWriteConfig
import com.gladow.indexer4s.elasticsearch.index_ops.{AliasSwitching, EsOpsClient, IndexDeletion}
import com.sksamuel.elastic4s.Indexable

import scala.concurrent.{ExecutionContext, Future}

object IndexableStream {
  type IndexAction = Future[Either[IndexError, RunResult]]

  def addRunStep(actionDone: => IndexAction, nextStep: => Future[Either[IndexError, StageSucceeded]])
    (implicit ex: ExecutionContext): IndexAction =
    (for {
      indexResult <- EitherT(actionDone)
      success <- EitherT(nextStep)
        .leftMap(_.copy(succeededStages = indexResult.succeededStages.toList))
    } yield RunResult(indexResult.succeededStages :+ success: _*))
      .value
}

class IndexableStream[A](source: Source[A, NotUsed])
  (implicit system: ActorSystem, materializer: ActorMaterializer, indexable: Indexable[A]) {

  def runStream(esConf: ElasticWriteConfig)(implicit ex: ExecutionContext): IndexAction = {
    val esWriter = ElasticWriter[A](esConf)
    IndexableStream.addRunStep(
      actionDone = EitherT(esWriter.createNewIndex).map(RunResult(_)).value,
      nextStep = FullStream.run(source, esWriter.esSink, esConf.logWriteSpeedEvery)
    )
  }

  //todo add threshold option
  def switchAliasFrom(alias: String, minThreshold: Double = 0.95, maxThreshold: Double = 1.25): IndexableStreamWithSwitching =
    new IndexableStreamWithSwitching((conf, ex) => runStream(conf)(ex), alias, minThreshold, maxThreshold)

  def deleteOldIndices(keep: Int, aliasProtection: Boolean = true): IndexableStreamWithDeletion =
    new IndexableStreamWithDeletion((conf, ex) => runStream(conf)(ex), keep, aliasProtection)
}

class IndexableStreamWithSwitching(run: (ElasticWriteConfig, ExecutionContext) => IndexAction, alias: String, minT: Double, maxT: Double) {

  def runStream(esConf: ElasticWriteConfig)(implicit ex: ExecutionContext): IndexAction =
    IndexableStream.addRunStep(
      actionDone = run(esConf, ex),
      nextStep = AliasSwitching(EsOpsClient(esConf.client), minT, maxT).switchAlias(alias, esConf.indexName)
    )

  def deleteOldIndices(keep: Int, aliasProtection: Boolean = true): IndexableStreamWithDeletion =
    new IndexableStreamWithDeletion((conf, ex) => runStream(conf)(ex), keep, aliasProtection)
}

class IndexableStreamWithDeletion(run: (ElasticWriteConfig, ExecutionContext) => IndexAction, keep: Int, aliasProtection: Boolean) {

  def runStream(esConf: ElasticWriteConfig)(implicit ex: ExecutionContext): IndexAction =
    IndexableStream.addRunStep(
      actionDone = run(esConf, ex),
      nextStep = IndexDeletion(EsOpsClient(esConf.client))
        .deleteOldest(esConf.indexPrefix, esConf.indexName ,keep, aliasProtection)
    )
}



