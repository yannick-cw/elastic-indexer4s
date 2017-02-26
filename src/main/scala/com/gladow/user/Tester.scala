package com.gladow.user

import akka.stream.scaladsl.Source
import com.gladow.indexer4s.ElasticIndexer4s
import com.gladow.indexer4s.elasticsearch.elasic_config.ElasticWriteConfig
import io.circe.generic.auto._

import scala.concurrent.duration._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

object Tester extends App {

  case class Tester(i: Int, s: String)

  val dummySource = Source.repeat("10").take(100000)
    .mapAsync(2)(s => Future.successful(s.toInt))
    .map(i => Tester(i, "done"))

  val config = ElasticWriteConfig(
    List("localhost"),
    "9300",
    "elasticsearch",
    "experimental",
    "docs",
    logWriteSpeedEvery = 10 seconds)

  ElasticIndexer4s
    .from(dummySource)
    .switchAliasFrom(alias = "newAlias")
    .deleteOldIndices(keep = 0, true)
    .runStream(config)
    .onComplete {
      case Success(res) => res.fold(println, println)
      case Failure(ex) => ex.printStackTrace
    }
}
