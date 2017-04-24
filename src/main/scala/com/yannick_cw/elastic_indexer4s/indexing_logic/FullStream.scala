package com.yannick_cw.elastic_indexer4s.indexing_logic

import akka.NotUsed
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import com.yannick_cw.elastic_indexer4s.Index_results.{IndexError, StageSucceeded, StageSuccess}
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

object FullStream extends LazyLogging {

  private def countAntLogSink[A](logPer: FiniteDuration): Sink[A, Future[Int]] = Flow[A]
    .groupedWithin(Int.MaxValue, logPer)
    .map(_.length)
    .map { elementsPerTime =>
      logger.info(s"Indexed $elementsPerTime elements last $logPer")
      elementsPerTime
    }.toMat(Sink.reduce[Int](_ + _))(Keep.right)

  def run[A](source: Source[A, NotUsed], sink: Sink[A, Future[Unit]], logSpeedInterval: FiniteDuration)
    (implicit materializer: ActorMaterializer, ex: ExecutionContext): Future[Either[IndexError, StageSucceeded]] =
    (for {
      count <- source
        .alsoToMat(countAntLogSink(logSpeedInterval))(Keep.right)
        .toMat(sink)(Keep.both)
        .mapMaterializedValue{ case(fCount, fDone) => fDone.flatMap(_ => fCount) }
        .run()
    } yield Right(StageSuccess(s"Indexed $count documents successfully")))
      .recover { case NonFatal(t) =>
        Left(IndexError("Writing documents failed.", Some(t)))
      }
}
