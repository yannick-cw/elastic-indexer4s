package com.yannick_cw.elastic_indexer4s.indexing_logic

import akka.NotUsed
import akka.stream.scaladsl.Source
import cats.Monad
import cats.data.EitherT
import com.sksamuel.elastic4s.streams.RequestBuilder
import com.yannick_cw.elastic_indexer4s.Index_results.{IndexError, RunResult, StageSucceeded}
import com.yannick_cw.elastic_indexer4s.elasticsearch.EsAccess

trait IndexOps[F[_]] {
  def write[A: RequestBuilder](source: Source[A, NotUsed]): EitherT[F, IndexError, RunResult]
  def addSwitch(writeDone: EitherT[F, IndexError, RunResult],
                minT: Double,
                maxT: Double,
                alias: String): EitherT[F, IndexError, RunResult]
  def addDelete(writeDone: EitherT[F, IndexError, RunResult],
                keep: Int,
                aliasProtection: Boolean): EitherT[F, IndexError, RunResult]
}

class IndexingWithEs[F[_]: Monad](implicit ES: EsAccess[F]) extends IndexOps[F] {
  private def addRunStep(actionDone: EitherT[F, IndexError, RunResult],
                         nextStep: EitherT[F, IndexError, StageSucceeded]) =
    for {
      indexResult <- actionDone
      success <- nextStep
        .leftMap(_.copy(succeededStages = indexResult.succeededStages.toList))
    } yield RunResult(indexResult.succeededStages :+ success: _*)

  def write[A: RequestBuilder](source: Source[A, NotUsed]): EitherT[F, IndexError, RunResult] =
    addRunStep(ES.createIndex().map(RunResult(_)), ES.indexSource(source))

  def addSwitch(writeDone: EitherT[F, IndexError, RunResult],
                minT: Double,
                maxT: Double,
                alias: String): EitherT[F, IndexError, RunResult] =
    addRunStep(writeDone, ES.switchAlias(minT, maxT, alias))

  def addDelete(writeDone: EitherT[F, IndexError, RunResult],
                keep: Int,
                aliasProtection: Boolean): EitherT[F, IndexError, RunResult] =
    addRunStep(writeDone, ES.deleteOldIndices(keep, aliasProtection))

}
