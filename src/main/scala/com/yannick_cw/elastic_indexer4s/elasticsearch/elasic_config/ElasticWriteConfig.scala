package com.yannick_cw.elastic_indexer4s.elasticsearch.elasic_config

import com.sksamuel.elastic4s.http.{ElasticClient, ElasticNodeEndpoint}
import org.apache.http.HttpHost
import org.elasticsearch.client.RestClient
import org.elasticsearch.client.sniff.Sniffer
import org.joda.time.DateTime

import scala.concurrent.duration.{FiniteDuration, _}

case class ElasticWriteConfig(
    elasticNodeEndpoints: List[ElasticNodeEndpoint],
    indexPrefix: String,
    docType: String,
    mappingSetting: MappingSetting = TypedMappingSetting(),
    writeBatchSize: Int = 50,
    writeConcurrentRequest: Int = 10,
    writeMaxAttempts: Int = 5,
    logWriteSpeedEvery: FiniteDuration = 1 minute,
    waitForElasticTimeout: FiniteDuration = 5 seconds,
    sniffCluster: Boolean = false
) {
  val indexName: String = indexPrefix + "_" + new DateTime().toString("yyyy-MM-dd't'HH:mm:ss")

  lazy val restClient: RestClient =
    RestClient
      .builder(elasticNodeEndpoints.map(e => new HttpHost(e.host, e.port, "http")): _*)
      .build()

  lazy val client: ElasticClient = {
    if (sniffCluster) {
      // sniffs every 5 minutes for the best hosts to connect to
      Sniffer.builder(restClient).build()
    }
    ElasticClient.fromRestClient(restClient)
  }
}

object ElasticWriteConfig {
  def apply(
      esNodeEndpoints: List[ElasticNodeEndpoint],
      esTargetIndexPrefix: String,
      esTargetType: String
  ): ElasticWriteConfig =
    new ElasticWriteConfig(esNodeEndpoints, esTargetIndexPrefix, esTargetType)
}
