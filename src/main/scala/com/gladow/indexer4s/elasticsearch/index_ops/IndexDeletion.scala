package com.gladow.indexer4s.elasticsearch.index_ops

import scala.concurrent.{ExecutionContext, Future}
import cats.implicits._
import com.gladow.indexer4s.Index_results.{IndexError, StageSucceeded, StageSuccess}

class IndexDeletion(esClient: EsOpsClientApi)(implicit ec: ExecutionContext) {

  import esClient._

  def deleteOldest(indexPrefix: String, newIndex: String, keep: Int, protectAlias: Boolean): Future[Either[IndexError, StageSucceeded]] = for {
    allIndices <- allIndicesWithAliasInfo
    toDelete = allIndices
      .sortBy(_.creationTime)
      .filterNot(_.index == newIndex)
      .dropRight(keep)
      .filter(info => if (protectAlias) info.aliases.isEmpty else true)
      .map(_.index)
      .filter(_.startsWith(indexPrefix))
    _ <- toDelete.traverse(delete)
  } yield Right(StageSuccess(s"Deleted indices: ${toDelete.mkString("[", ",", "]")}"))
}

object IndexDeletion {
  def apply(esClient: EsOpsClientApi)(implicit ec: ExecutionContext): IndexDeletion = new IndexDeletion(esClient)
}
