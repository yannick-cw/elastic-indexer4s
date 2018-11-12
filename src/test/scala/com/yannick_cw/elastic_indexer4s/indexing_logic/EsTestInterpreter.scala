package com.yannick_cw.elastic_indexer4s.indexing_logic

import akka.NotUsed
import akka.stream.scaladsl.Source
import cats.data.WriterT._
import cats.data.{EitherT, Writer}
import com.sksamuel.elastic4s.bulk.BulkCompatibleRequest
import com.sksamuel.elastic4s.streams.RequestBuilder
import com.yannick_cw.elastic_indexer4s.Index_results.{IndexError, RunResult, StageSucceeded, StageSuccess}
import com.yannick_cw.elastic_indexer4s.elasticsearch.EsAccess
import com.yannick_cw.elastic_indexer4s.indexing_logic.TestMonad.W

sealed trait Command
case object CreateIndex extends Command
case object IndexIt     extends Command
case object Switch      extends Command
case object Delete      extends Command
object TestMonad {
  type W[A] = Writer[List[Command], A]
  implicit val r: RequestBuilder[String] = (t: String) => ???
}
object EsTestInterpreter extends EsAccess[W] {
  def createIndex(): EitherT[W, IndexError, StageSucceeded] =
    EitherT.liftF[W, IndexError, StageSucceeded](Writer(List(CreateIndex), StageSuccess("create")))

  def indexSource[A: RequestBuilder](source: Source[A, NotUsed]): EitherT[W, IndexError, StageSucceeded] =
    EitherT.liftF[W, IndexError, StageSucceeded](Writer(List(IndexIt), StageSuccess("index")))

  def switchAlias(minT: Double, maxT: Double, alias: String): EitherT[W, IndexError, StageSucceeded] =
    if (alias == "failAlias")
      EitherT[W, IndexError, StageSucceeded](
        Writer[List[Command], Either[IndexError, StageSucceeded]](List(Switch), Left(IndexError("fail"))))
    else EitherT.liftF[W, IndexError, StageSucceeded](Writer(List(Switch), StageSuccess("switch")))

  def deleteOldIndices(keep: Int, aliasProtection: Boolean): EitherT[W, IndexError, StageSucceeded] =
    EitherT.liftF[W, IndexError, StageSucceeded](Writer(List(Delete), StageSuccess("delete")))
}

object IndexOpsTestInterpreter extends IndexOps[W] {
  def write[A: RequestBuilder](source: Source[A, NotUsed]): EitherT[W, IndexError, RunResult] =
    EitherT.liftF[W, IndexError, RunResult](Writer(List(IndexIt), RunResult(StageSuccess("index"))))

  def addSwitch(writeDone: EitherT[W, IndexError, RunResult],
                minT: Double,
                maxT: Double,
                alias: String): EitherT[W, IndexError, RunResult] =
    if (alias == "failAlias")
      EitherT[W, IndexError, RunResult](
        Writer[List[Command], Either[IndexError, RunResult]](List(Switch), Left(IndexError("fail"))))
    else EitherT.liftF[W, IndexError, RunResult](Writer(List(Switch), RunResult(StageSuccess("switch"))))

  def addDelete(writeDone: EitherT[W, IndexError, RunResult],
                keep: Int,
                aliasProtection: Boolean): EitherT[W, IndexError, RunResult] =
    EitherT.liftF[W, IndexError, RunResult](Writer(List(Delete), RunResult(StageSuccess("delete"))))

}
