package com.yannick_cw.indexer4s.indexing_logic

import akka.NotUsed
import akka.stream.scaladsl.Source
import cats.{Monad, ~>}
import com.yannick_cw.indexer4s.Index_results.{IndexError, RunResult}
import com.yannick_cw.indexer4s.indexing_logic.IndexLogic._

class IndexableStream[A, M[_]](source: Source[A, NotUsed], interpreter: (IndexAction ~> M))
  (implicit M: Monad[M]) {

  private val writeStep = write(source)

  def run: M[Either[IndexError, RunResult]] =
    writeStep.value.foldMap(interpreter)

  def switchAliasFrom(alias: String, minT: Double = 0.95, maxT: Double = 1.25): IndexableStreamWithSwitching[A, M] =
    new IndexableStreamWithSwitching(writeStep, minT, maxT, alias, interpreter)

  def deleteOldIndices(keep: Int, aliasProtection: Boolean = true): IndexableStreamWithDeletion[A, M] =
    new IndexableStreamWithDeletion(writeStep, keep, aliasProtection, interpreter)
}

class IndexableStreamWithSwitching[A, M[_]](
  done: StageAction[RunResult],
  minT: Double,
  maxT: Double,
  alias: String,
  interpreter: (IndexAction ~> M))(implicit M: Monad[M]) {

  private val switchStep = addSwitch(done, minT, maxT, alias)

  def run: M[Either[IndexError, RunResult]] =
    switchStep.value.foldMap(interpreter)

  def deleteOldIndices(keep: Int, aliasProtection: Boolean = true): IndexableStreamWithDeletion[A, M] =
    new IndexableStreamWithDeletion(switchStep, keep, aliasProtection, interpreter)
}

class IndexableStreamWithDeletion[A, M[_]](
  done: StageAction[RunResult],
  keep: Int,
  aliasProtection: Boolean,
  interpreter: (IndexAction ~> M))(implicit M: Monad[M]) {

  def run: M[Either[IndexError, RunResult]] =
    addDelete(done, keep, aliasProtection).value.foldMap(interpreter)
}

