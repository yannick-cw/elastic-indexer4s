package com.gladow.indexer4s

import akka.NotUsed
import akka.stream.scaladsl.Source
import cats.data.EitherT
import cats.free.Free
import cats.free.Free.liftF
import com.gladow.indexer4s.Index_results.{IndexError, RunResult, StageSucceeded}
import com.gladow.indexer4s.IndexableStream._

object IndexableStream {
  sealed trait IndexAction[A]
  type FreeIndexAction[A] = Free[IndexAction, A]
  type Result = Either[IndexError, RunResult]
  type StageResult = Either[IndexError, StageSucceeded]

  case object CreateIndex extends IndexAction[StageResult]
  case class IndexSource[A](source: Source[A, NotUsed]) extends IndexAction[StageResult]
  case class SwitchAlias(minT: Double, maxT: Double, alias: String) extends IndexAction[StageResult]
  case class DeleteOldIndices(keep: Int, aliasProtection: Boolean) extends IndexAction[StageResult]

  def createIndex: FreeIndexAction[StageResult] = liftF(CreateIndex)
  def indexSource[A](source: Source[A, NotUsed]): FreeIndexAction[StageResult] =
    liftF[IndexAction, StageResult](IndexSource(source))
  def switchAlias(minT: Double, maxT: Double, alias: String): FreeIndexAction[StageResult] =
    liftF(SwitchAlias(minT, maxT, alias))
  def deleteOldIndices(keep: Int, aliasProtection: Boolean): FreeIndexAction[StageResult] =
    liftF(DeleteOldIndices(keep, aliasProtection))

  def write[A](source: Source[A, NotUsed]): EitherT[FreeIndexAction, IndexError, RunResult] = for {
    created <- EitherT(createIndex)
    indexed <- EitherT(indexSource(source))
      .leftMap(_.copy(succeededStages = created :: Nil))
  } yield RunResult(created, indexed)

  def writeAndSwitch(done: EitherT[FreeIndexAction, IndexError, RunResult])(alias: String, minThreshold: Double, maxThreshold: Double) =
    addRunStep(done, EitherT(switchAlias(minThreshold, maxThreshold, alias)))

  def writeSwitchDelete[A](done: EitherT[FreeIndexAction, IndexError, RunResult])
    (keep: Int, aliasProtection: Boolean) =
    addRunStep(done, EitherT(deleteOldIndices(keep, aliasProtection)))

  def writeDelete[A](done: EitherT[FreeIndexAction, IndexError, RunResult])(keep: Int, aliasProtection: Boolean) =
    addRunStep(done, EitherT(deleteOldIndices(keep, aliasProtection)))

  def addRunStep(
    actionDone: => EitherT[FreeIndexAction, IndexError, RunResult],
    nextStep: => EitherT[FreeIndexAction, IndexError, StageSucceeded]) =
    for {
      indexResult <- actionDone
      success <- nextStep
        .leftMap(_.copy(succeededStages = indexResult.succeededStages.toList))
    } yield RunResult(indexResult.succeededStages :+ success: _*)
}

class IndexableStream[A](source: Source[A, NotUsed]) {
  private lazy val write1 = write(source)

  def runStream: FreeIndexAction[Result] = write1.value

  def switchAliasFrom(alias: String, minThreshold: Double = 0.95, maxThreshold: Double = 1.25): IndexableStreamWithSwitching =
    new IndexableStreamWithSwitching(writeAndSwitch(write1)(alias, minThreshold, maxThreshold))

  def deleteOldIndices(keep: Int, aliasProtection: Boolean = true): IndexableStreamWithDeletion =
    new IndexableStreamWithDeletion(writeDelete(write1)(keep, aliasProtection))
}

class IndexableStreamWithSwitching(run: EitherT[FreeIndexAction, IndexError, RunResult]) {

  def runStream: FreeIndexAction[Result] = run.value

  def deleteOldIndices(keep: Int, aliasProtection: Boolean = true): IndexableStreamWithDeletion =
    new IndexableStreamWithDeletion(writeSwitchDelete(run)(keep, aliasProtection))
}

class IndexableStreamWithDeletion(run: EitherT[FreeIndexAction, IndexError, RunResult]) {
  def runStream: FreeIndexAction[Result] = run.value
}



