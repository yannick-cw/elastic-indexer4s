package com.yannick_cw.elastic_indexer4s.elasticsearch.index_ops

import cats.data.EitherT
import cats.implicits._
import com.yannick_cw.elastic_indexer4s.Index_results.{IndexError, StageSucceeded}

import scala.concurrent.{ExecutionContext, Future, blocking}
import scala.util.control.NonFatal

class AliasSwitching(esClient: EsOpsClientApi, waitForElastic: Long, minThreshold: Double, maxThreshold: Double)(
    implicit ec: ExecutionContext) {

  import esClient._

  def switchAlias(alias: String, newIndexName: String): Future[Either[IndexError, StageSucceeded]] =
    trySwitching(alias, newIndexName)
      .recover {
        case NonFatal(ex) =>
          Left(IndexError("Could not switch alias.", Some(ex)))
      }

  private def trySwitching(alias: String, newIndexName: String): Future[Either[IndexError, StageSucceeded]] =
    (for {
      _       <- EitherT.liftF[Future, IndexError, Unit](Future(blocking(Thread.sleep(waitForElastic))))
      oldSize <- latestIndexWithAliasSize(alias)
      newSize <- sizeFor(newIndexName)
      optSwitchRes <- oldSize
        .traverse(oldIndexSize => switchAliasBetweenIndices(oldIndexSize, newSize, alias, newIndexName))
      switchRes <- optSwitchRes match {
        case None =>
          addAliasToIndex(newIndexName, alias)
            .map(_ => NewAliasCreated(s"Added alias $alias to index $newIndexName"): StageSucceeded)
        case Some(x) => EitherT.pure[Future, IndexError](x)
      }
    } yield switchRes).value

  private def switchAliasBetweenIndices(oldSize: Long,
                                        newSize: Long,
                                        alias: String,
                                        newIndexName: String): OpsResult[StageSucceeded] = {
    val percentage = newSize / oldSize.toDouble
    if (checkThreshold(percentage))
      switchAliasToIndex(alias, newIndexName)
        .map(_ => AliasSwitched(s"Switched alias, new index size is ${(percentage * 100).toInt}% of old index"))
    else
      EitherT.leftT(
        IndexError(
          s"Switching failed, new index size is ${(percentage * 100).toInt}% of old index,\n" +
            s" $oldSize documents in old index with alias $alias, $newSize documents in new index $newIndexName.\n\n" +
            s"If you think the size of the new index is not correct, try to increase the `waitForElasticTimeout` property in the config." +
            s"This run spent ${waitForElastic / 1000} seconds waiting"))
  }

  private def checkThreshold(percentage: Double): Boolean = minThreshold < percentage && percentage <= maxThreshold
}

object AliasSwitching {
  def apply(esClient: EsOpsClientApi, minThreshold: Double, maxThreshold: Double, waitForElastic: Long)(
      implicit ec: ExecutionContext): AliasSwitching =
    new AliasSwitching(esClient, waitForElastic, minThreshold, maxThreshold)
}

case class AliasSwitched(override val msg: String)   extends StageSucceeded
case class NewAliasCreated(override val msg: String) extends StageSucceeded
