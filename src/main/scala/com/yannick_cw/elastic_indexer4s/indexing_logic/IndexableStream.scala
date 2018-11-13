package com.yannick_cw.elastic_indexer4s.indexing_logic

import akka.NotUsed
import akka.stream.scaladsl.Source
import cats.Monad
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.data.EitherT
import com.sksamuel.elastic4s.streams.RequestBuilder
import com.yannick_cw.elastic_indexer4s.Index_results.{IndexError, RunResult}

class IndexableStream[A: RequestBuilder, M[_]: Monad](source: Source[A, NotUsed], shutdown: () => M[Unit])(
    implicit indexing: IndexOps[M]) {
  private val writeStep = indexing.write(source)

  def run: M[Either[IndexError, RunResult]] =
    writeStep.value.flatMap(res => shutdown().map(_ => res))

  def switchAliasFrom(alias: String, minT: Double = 0.95, maxT: Double = 1.25): IndexableStreamWithSwitching[A, M] =
    new IndexableStreamWithSwitching(writeStep, minT, maxT, alias, shutdown)

  def deleteOldIndices(keep: Int, aliasProtection: Boolean = true): IndexableStreamWithDeletion[A, M] =
    new IndexableStreamWithDeletion(writeStep, keep, aliasProtection, shutdown)
}

class IndexableStreamWithSwitching[A, M[_]](done: EitherT[M, IndexError, RunResult],
                                            minT: Double,
                                            maxT: Double,
                                            alias: String,
                                            shutdown: () => M[Unit])(implicit M: Monad[M], indexing: IndexOps[M]) {

  private val switchStep = indexing.addSwitch(done, minT, maxT, alias)

  def run: M[Either[IndexError, RunResult]] =
    switchStep.value.flatMap(res => shutdown().map(_ => res))

  def deleteOldIndices(keep: Int, aliasProtection: Boolean = true): IndexableStreamWithDeletion[A, M] =
    new IndexableStreamWithDeletion(switchStep, keep, aliasProtection, shutdown)
}

class IndexableStreamWithDeletion[A, M[_]: Monad](done: EitherT[M, IndexError, RunResult],
                                                  keep: Int,
                                                  aliasProtection: Boolean,
                                                  shutdown: () => M[Unit])(implicit indexing: IndexOps[M]) {

  def run: M[Either[IndexError, RunResult]] =
    indexing.addDelete(done, keep, aliasProtection).value.flatMap(res => shutdown().map(_ => res))
}
