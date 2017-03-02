package com.gladow.indexer4s.indexing_logic

import akka.NotUsed
import akka.stream.scaladsl.Source
import cats.data.EitherT
import cats.free.Free
import cats.free.Free.liftF
import com.gladow.indexer4s.Index_results.{IndexError, RunResult, StageSucceeded}

object IndexLogic {
  sealed trait IndexAction[A]
  type FreeIndexAction[A] = Free[IndexAction, A]
  type StageResult = Either[IndexError, StageSucceeded]
  type StageAction[A] = EitherT[FreeIndexAction, IndexError, A]

  case object CreateIndex extends IndexAction[StageResult]
  case class IndexSource[A](source: Source[A, NotUsed]) extends IndexAction[StageResult]
  case class SwitchAlias(minT: Double, maxT: Double, alias: String) extends IndexAction[StageResult]
  case class DeleteOldIndices(keep: Int, aliasProtection: Boolean) extends IndexAction[StageResult]

  // lifts the Algebra into a free context and than into EitherT for easier operations
  private def createIndex: StageAction[StageSucceeded] = EitherT(liftF(CreateIndex): FreeIndexAction[StageResult])
  private def indexSource[A](source: Source[A, NotUsed]): StageAction[StageSucceeded] =
    EitherT(liftF[IndexAction, StageResult](IndexSource(source)): FreeIndexAction[StageResult])
  private def switchAlias(minT: Double, maxT: Double, alias: String): StageAction[StageSucceeded] =
    EitherT(liftF(SwitchAlias(minT, maxT, alias)): FreeIndexAction[StageResult])
  private def deleteOldIndices(keep: Int, aliasProtection: Boolean): StageAction[StageSucceeded] =
    EitherT(liftF(DeleteOldIndices(keep, aliasProtection)): FreeIndexAction[StageResult])

  private def addRunStep(actionDone: StageAction[RunResult], nextStep: StageAction[StageSucceeded]) =
    for {
      indexResult <- actionDone
      success <- nextStep
        .leftMap(_.copy(succeededStages = indexResult.succeededStages.toList))
    } yield RunResult(indexResult.succeededStages :+ success: _*)

  def write[A](source: Source[A, NotUsed]): StageAction[RunResult] = for {
    created <- createIndex
    indexed <- indexSource(source)
      .leftMap(_.copy(succeededStages = created :: Nil))
  } yield RunResult(created, indexed)

  def addSwitch(writeDone: StageAction[RunResult], minT: Double, maxT: Double, alias: String) =
    addRunStep(writeDone, switchAlias(minT, maxT, alias))

  def addDelete(writeDone: StageAction[RunResult], keep: Int, aliasProtection: Boolean) =
    addRunStep(writeDone, deleteOldIndices(keep, aliasProtection))
}
