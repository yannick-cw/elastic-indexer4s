package com.gladow.indexer4s.elasticsearch.index_ops

import scala.concurrent.{ExecutionContext, Future}
import cats.implicits._
import com.gladow.indexer4s.IndexResults.{IndexError, StageSucceeded}

import scala.util.Right
import scala.util.control.NonFatal

class AliasSwitching(esClient: EsOpsClientApi, waitForElastic: Int = 1000)
  (implicit ec: ExecutionContext) {

  import esClient._

  def switchAlias(alias: String, newIndexName: String): Future[Either[IndexError, StageSucceeded]] = trySwitching(alias, newIndexName)
    .recover { case NonFatal(ex) =>
        Left(IndexError("Could not switch alias with: " + ex.toString + "\n" + ex.getStackTrace.mkString("\n")))
    }

  private def trySwitching(alias: String, newIndexName: String): Future[Either[IndexError, StageSucceeded]] = for {
    _ <- Future(Thread.sleep(waitForElastic))
    oldSize <- latestIndexWithAliasSize(alias)
    newSize <- sizeFor(newIndexName)
    optSwitchRes <- oldSize.traverse(size => switchAliasBetweenIndices(newSize / size.toDouble, alias, newIndexName))
    switchRes <- optSwitchRes match {
      case None => addAliasToIndex(newIndexName, alias)
        .map(_ => Right(NewAliasCreated(s"Added alias $alias to index $newIndexName")))
      case Some(x) => Future.successful(x)
    }
  } yield switchRes

  private def switchAliasBetweenIndices(percentage: Double, alias: String, newIndexName: String): Future[Either[IndexError, StageSucceeded]] =
    if (thresholdCheck(percentage)) switchAliasToIndex(alias, newIndexName)
      .map(_ => Right(AliasSwitched(s"Switched alias, new index size is ${(percentage * 100).toInt}% of old index")))
    else Future.successful(Left(IndexError(s"Switching failed, new index size is ${(percentage * 100).toInt}% of old index")))

  private def thresholdCheck(percentage: Double): Boolean = 0.95 < percentage && percentage <= 1.25
}

case class AliasSwitched(override val msg: String) extends StageSucceeded
case class NewAliasCreated(override val msg: String) extends StageSucceeded