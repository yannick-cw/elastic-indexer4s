package com.gladow.user

import akka.stream.scaladsl.Source
import com.gladow.indexer4s.ElasticIndexer4s
import com.gladow.indexer4s.elasticsearch.elasic_config.ElasticWriteConfig
import com.sksamuel.elastic4s.circe.indexableWithCirce
import io.circe.generic.auto._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}

object Tester extends App {

  case class Tester(i: Int, s: String)

  val dummySource = Source
    .repeat("10")
    .take(100000)
    .mapAsync(2)(s => Future.successful(s.toInt))
    .map(i => Tester(i, "done"))

  val config = ElasticWriteConfig(
    hosts = List("localhost"),
    port = 9300,
    cluster = "elasticsearch",
    indexPrefix = "experimental",
    docType = "docs",
    logWriteSpeedEvery = 10 seconds,
    shards = Some(2),
    replicas = Some(2)
  )

  ElasticIndexer4s(config)
    .from(dummySource)
    .switchAliasFrom(alias = "newAlias")
    .deleteOldIndices(keep = 0, true)
    .run
    .onComplete {
      case Success(res) => res.fold(println, println)
      case Failure(ex) => ex.printStackTrace
    }
}
