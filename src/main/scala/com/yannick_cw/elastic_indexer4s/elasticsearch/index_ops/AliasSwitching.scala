package com.yannick_cw.elastic_indexer4s.elasticsearch.index_ops

import scala.concurrent.{ExecutionContext, Future}
import cats.implicits._
import com.yannick_cw.elastic_indexer4s.Index_results.{IndexError, StageSucceeded}

import scala.util.Right
import scala.util.control.NonFatal

class AliasSwitching(esClient: EsOpsClientApi, waitForElastic: Long, minThreshold: Double, maxThreshold: Double)
  (implicit ec: ExecutionContext) {

  import esClient._

  def switchAlias(alias: String, newIndexName: String): Future[Either[IndexError, StageSucceeded]] = trySwitching(alias, newIndexName)
    .recover { case NonFatal(ex) =>
        Left(IndexError("Could not switch alias.", Some(ex)))
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
    if (checkThreshold(percentage)) switchAliasToIndex(alias, newIndexName)
      .map(_ => Right(AliasSwitched(s"Switched alias, new index size is ${(percentage * 100).toInt}% of old index")))
    else Future.successful(Left(IndexError(s"Switching failed, new index size is ${(percentage * 100).toInt}% of old index")))

  private def checkThreshold(percentage: Double): Boolean = minThreshold < percentage && percentage <= maxThreshold
}

object AliasSwitching {
  def apply(esClient: EsOpsClientApi,  minThreshold: Double, maxThreshold: Double, waitForElastic: Long)
    (implicit ec: ExecutionContext): AliasSwitching = new AliasSwitching(esClient, waitForElastic, minThreshold, maxThreshold)
}

case class AliasSwitched(override val msg: String) extends StageSucceeded
case class NewAliasCreated(override val msg: String) extends StageSucceeded