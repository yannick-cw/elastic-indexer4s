## Elastic Indexer 4s

[![Build Status](https://travis-ci.org/yannick-cw/elastic-indexer4s.svg?branch=master)](https://travis-ci.org/yannick-cw/elastic-indexer4s)
[![Coverage Status](https://coveralls.io/repos/github/yannick-cw/elastic-indexer4s/badge.svg?branch=master)](https://coveralls.io/github/yannick-cw/elastic-indexer4s?branch=master)

## Usage Example

```scala
import akka.stream.scaladsl.Source
import com.yannick_cw.indexer4s.ElasticIndexer4s
import com.yannick_cw.indexer4s.elasticsearch.elasic_config.ElasticWriteConfig
import com.sksamuel.elastic4s.circe.indexableWithCirce
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}

import com.sksamuel.elastic4s.circe.indexableWithCirce
import io.circe.generic.auto._

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
```