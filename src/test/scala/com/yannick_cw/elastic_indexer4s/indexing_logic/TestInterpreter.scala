package com.yannick_cw.elastic_indexer4s.indexing_logic

import akka.NotUsed
import akka.stream.scaladsl.Source
import cats.{Id, ~>}
import com.yannick_cw.elastic_indexer4s.Index_results.{IndexError, StageSuccess}
import com.yannick_cw.elastic_indexer4s.indexing_logic.IndexLogic._

import scala.collection.mutable

class TestInterpreter[Entity] extends (IndexAction ~> Id) {
  val bufferOfActions = mutable.Buffer[IndexAction[_]]()

  override def apply[A](fa: IndexAction[A]): Id[A] = {
    bufferOfActions += fa
    fa match {
      case CreateIndex                                  => Right(StageSuccess("create"))
      case IndexSource(source: Source[Entity, NotUsed]) => Right(StageSuccess("index"))
      case SwitchAlias(minT, maxT, "failAlias")         => Left(IndexError("failed"))
      case SwitchAlias(minT, maxT, alias)               => Right(StageSuccess("switch"))
      case DeleteOldIndices(keep, aliasProtection)      => Right(StageSuccess("delete"))
    }
  }
}
