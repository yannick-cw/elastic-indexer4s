package com.gladow.indexer4s

import akka.NotUsed
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import com.gladow.indexer4s.Index_results.{IndexError, StageSucceeded, StageSuccess}
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import scala.concurrent.duration._

object FullStream extends LazyLogging {

  def countAntLogSink[A](logPer: FiniteDuration): Sink[A, Future[Int]] = Flow[A]
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
    } yield Right(StageSuccess(s"Indexed $count documents successfullyy")))
      .recover { case NonFatal(t) =>
        Left(IndexError("Writing documents failed with: " + t.getStackTrace.mkString("\n")))
      }
}
