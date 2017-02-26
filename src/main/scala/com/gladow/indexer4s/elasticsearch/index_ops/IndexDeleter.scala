package com.gladow.indexer4s.elasticsearch.index_ops

import scala.concurrent.{ExecutionContext, Future}
import cats.implicits._
import com.gladow.indexer4s.IndexResults.{IndexError, StageSucceeded, StageSuccess}

class IndexDeleter(esClient: EsOpsClientApi)(implicit ec: ExecutionContext) {

  import esClient._

  def deleteOldest(indexPrefix: String, newIndex: String, keep: Int, protectAlias: Boolean): Future[Either[IndexError, StageSucceeded]] = for {
    allIndices <- allIndicesWithAliasInfo
    toDelete = allIndices
      .sortBy(_._3)
      .dropRight(keep)
      .filter { case (_, hasAlias, _) => if (protectAlias) !hasAlias else true }
      .map(_._1)
      .filter(_.startsWith(indexPrefix))
      .filterNot(_ == newIndex)
    _ <- toDelete
      .traverse(delete)
  } yield Right(StageSuccess(s"Deleted indices: ${toDelete.mkString("[", ",", "]")}"))
}
