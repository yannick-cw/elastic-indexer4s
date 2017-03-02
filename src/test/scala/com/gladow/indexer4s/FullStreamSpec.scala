package com.gladow.indexer4s

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import com.gladow.indexer4s.Index_results.{IndexError, StageSuccess}
import com.gladow.indexer4s.indexing_logic.FullStream
import com.gladow.indexer4s.specs.AsyncSpec

import scala.concurrent.Future
import scala.concurrent.duration._

class FullStreamSpec extends AsyncSpec {
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

  "The FullStream" should {
    "write all elements to the sink and log the total count" in {
      val source = Source.repeat("test").take(987)
      val sink = Sink.ignore.mapMaterializedValue(_.map(_ => ()))

      FullStream.run(source, sink, 10 seconds).map(res =>
        res.right.value shouldBe StageSuccess(s"Indexed 987 documents successfully")
      )
    }

    "fail if an exception is thrown during processing" in {
      val source = Source.repeat("test").take(987)
      val failingSink = Sink.ignore.mapMaterializedValue(_ => Future.failed(new IllegalArgumentException))

      FullStream.run(source, failingSink, 10 seconds).map(res =>
        res.left.value shouldBe an[IndexError]
      )
    }
  }

  override protected def afterAll(): Unit = {
    materializer.shutdown()
    system.terminate()
  }
}
