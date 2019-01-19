## ElasticIndexer4s

[![Build Status](https://travis-ci.org/yannick-cw/elastic-indexer4s.svg?branch=master)](https://travis-ci.org/yannick-cw/elastic-indexer4s)
[![Coverage Status](https://coveralls.io/repos/github/yannick-cw/elastic-indexer4s/badge.svg?branch=master)](https://coveralls.io/github/yannick-cw/elastic-indexer4s?branch=master)


### Overview

ElasticIndexer4s is a Library that helps you write to elasticsearch from your scala application.
The main purpose is to spare you from writing your own implementation for streaming data to elasticsearch
and taking care of switching alias and deleting old indicies.
It builds heavily on [elastic4s](https://github.com/sksamuel/elastic4s)

#### Why?

At work we had multiple scala jobs running to write elasticseach indices. These jobs were running on a
nightly basis and each one had its own implementation and follow up for switching to the new alias and deleting old indices.
So this is a perfect place to generify this task. This library does exactly that for your, write to elasticsearch and
afterwards optionally switch the alias to the newly written index and delete old versions of the index.
The only thing you have to provide are some settings, an `akka.stream.scaladsl.Source[A]` and a `com.sksamuel.elastic4s.Indexable[A]`. 


### Getting Started

ElasticIndexer4s is currently available for Scala 2.12, if you need it for scala 2.11 open an issue.

To get started with SBT, simply add the following to your `build.sbt`
file:

```scala
libraryDependencies += "io.github.yannick-cw" % "elastic_indexer4s_2.12" % "0.6.4"
```

First you need a Configuration:
```scala
import com.yannick_cw.elastic_indexer4s.elasticsearch.elasic_config.ElasticWriteConfig
import com.sksamuel.elastic4s.http.ElasticNodeEndpoint

// basic configuration, without mapping, settings or analyzers
// will create an index with test_index_<current_date>
val config = ElasticWriteConfig(
    esNodeEndpoints = List(ElasticNodeEndpoint("http", "localhost", 9200, None)),
    esTargetCluster = "elasticsearch",
    esTargetIndexPrefix = "test_index",
    esTargetType = "documents"
  )
```

Then you'll need a akka Source of data with an `Indexable`, in this case using circe to transform to json:
```scala
import akka.stream.scaladsl.Source
import com.sksamuel.elastic4s.Indexable
import io.circe.generic.auto._
import io.circe.syntax._

case class Document(s: String)
object Document {
  implicit val indexable = new Indexable[Document] {
    override def json(t: Document): String = t.asJson.noSpaces
  }
}

val dummySource = Source.single(Document("testDoc"))
```

In the last step you connect the Source and run the Indexer with optional steps:
```scala
import com.yannick_cw.elastic_indexer4s.ElasticIndexer4s

 val runResult: Future[Either[IndexError, RunResult]] = ElasticIndexer4s(config)
    .from(dummySource)
    .switchAliasFrom(alias = "alias", minT = 0.95, maxT = 1.25)
    .deleteOldIndices(keep = 2, aliasProtection = true)
    .run
```

Your result will either be a `RunResult` if all went fine or an `IndexError`, with detailed information on what
steps succeeded and what failed.
The switching and deleting are optional.

### More Information

#### Possible configuration

| config name             | meaning           |
| ---------------------- | ----------------- |
|`elasticNodeEndpoints: List[ElasticNodeEndpoint]`  |        all elasticsearch nodes to connect to       |
|`cluster: String`  |     the name of the elasticsearch cluster          |
|`indexPrefix: String`  |    the prefix that will be used in the index name, a date will be added           |
|`docType: String`  |      name for the type of documents to be writte to elasticsearch, will be used as elasticsearch `type`        |
|`mappingSetting: MappingSetting` |     all mappings and settings, see below for more details          |
|`writeBatchSize: Int`  |       the number of documents written in one batch, default to 50        |
|`writeConcurrentRequest: Int`  |     the number of parallel request to elasticsearch, defaults to 10          |
|`writeMaxAttempts: Int`  |     the retry attempts to write before failure, defaults to 5          |
|`logWriteSpeedEvery: FiniteDuration` |    the time interval in which it is logged how many documents were written, defaults to a minute           |
|`waitForElasticTimeout: FiniteDuratio` |    time to wait to count the documents before switching alias, default to 5 seconds           |
|`sniffCluster` |    activate or deactivate [sniffing feature] (https://www.elastic.co/guide/en/elasticsearch/client/java-api/current/transport-client.html#transport-client) for the client


The `MappingSetting` can be passed to specify mappings and settings for the index.
You can either pass a
```scala
case class TypedMappingSetting(
  analyzer: List[AnalyzerDefinition] = List.empty,
  mappings: List[MappingDefinition] = List.empty,
  shards: Option[Int] = None,
  replicas: Option[Int] = None
)
```
with analyzers and mappings defined in elastic4s syntax or pass a
 ```scala
 StringMappingSetting.unsafeString(source: String): Either[ParsingFailure, MappingSetting]
 ```
 which gives you a `parsingFailure` on creation if the string can't be parsed to json.
 
 The `.from([source])` method requires you to give an `Indexable` for the elements to be indexed. Alternatively
 you can use `.fromBuilder`, if you want to give an implicit `RequestBuilder`. This allows for more configuration
 for the indexing, e.g. you can specify which `id` or which `timestamp` to use.
 
 #### Alias switching
 
 You can add the `.switchAliasFrom` step to the process of writing, if you want to switch an alias from
 an old index to the newly written one. If no old index with this alias was found, the new index gets
 the alias without switching.
 There are three parameters you can pass to
 ```scala
 .switchAliasFrom(alias = "alias", minT = 0.95, maxT = 1.25)
 ```
 The `alias` to switch from and add to the new index, a `minT` threshold defining how many percent of the old
 index the new index must have as document count and the `maxT` defining how many more documents the
 new index is allowed to have to allow switching.

 #### Index deletion
 
 Another optional step is `.deleteOldIndices` which allows you to clean up old indices.
 It has two paramters
 ```scala
 .deleteOldIndices(keep = 2, aliasProtection = true)
```
where `keep` defines how many indices with the defined `indexPrefix` are left in the cluster and
with `aliasProtection`, which, if set to `true`, does not allow deletion of any index with and alias set.

#### Akka Stream
For writing the stream to elasticsearch a `system` and `materializer` are needed. You can either pass them in to the
`ElasticIndexer4s` or you can let ElasticIndexer4s create its own system.
Additionally you can pass in a `Decider` before starting to write, to control what happens on failure in the stream.
```scala
ElasticIndexer4s(config)
    .withDecider(decider)  
    .from(dummySource)
```

### Changelog

* 0.5.0: switch to using elasticsearch 6.x.x version, using http client instead of tcp client
* 0.5.1: greatly increase the detail level of failure reporting
* 0.6.0: breaking change moving to elastic4s `6.3.3`, dropping all integration tests, as they are not supported at this time anymore by elastic4s (only by spinning up a cluster yourself or embedded docker)
