package com.yannick_cw.elastic_indexer4s.indexing_logic

import akka.NotUsed
import akka.stream.scaladsl.Source
import cats.Monad
import cats.data.EitherT
import com.sksamuel.elastic4s.streams.RequestBuilder
import com.yannick_cw.elastic_indexer4s.Index_results.{IndexError, RunResult}

class IndexableStream[A: RequestBuilder, M[_]: Monad](source: Source[A, NotUsed])(implicit indexing: IndexOps[M]) {
  private val writeStep = indexing.write(source)

  def run: M[Either[IndexError, RunResult]] =
    writeStep.value

  def switchAliasFrom(alias: String, minT: Double = 0.95, maxT: Double = 1.25): IndexableStreamWithSwitching[A, M] =
    new IndexableStreamWithSwitching(writeStep, minT, maxT, alias)

  def deleteOldIndices(keep: Int, aliasProtection: Boolean = true): IndexableStreamWithDeletion[A, M] =
    new IndexableStreamWithDeletion(writeStep, keep, aliasProtection)
}

class IndexableStreamWithSwitching[A, M[_]](done: EitherT[M, IndexError, RunResult],
                                            minT: Double,
                                            maxT: Double,
                                            alias: String)(implicit M: Monad[M], indexing: IndexOps[M]) {

  private val switchStep = indexing.addSwitch(done, minT, maxT, alias)

  def run: M[Either[IndexError, RunResult]] =
    switchStep.value

  def deleteOldIndices(keep: Int, aliasProtection: Boolean = true): IndexableStreamWithDeletion[A, M] =
    new IndexableStreamWithDeletion(switchStep, keep, aliasProtection)
}

class IndexableStreamWithDeletion[A, M[_]](done: EitherT[M, IndexError, RunResult], keep: Int, aliasProtection: Boolean,
)(implicit indexing: IndexOps[M]) {

  def run: M[Either[IndexError, RunResult]] =
    indexing.addDelete(done, keep, aliasProtection).value
}
