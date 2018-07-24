package com.yannick_cw.elastic_indexer4s.elasticsearch.elasic_config

import com.sksamuel.elastic4s.http.{ElasticClient, ElasticNodeEndpoint, ElasticProperties}
import org.joda.time.DateTime

import scala.concurrent.duration.{FiniteDuration, _}

case class ElasticWriteConfig(
    elasticNodeEndpoints: List[ElasticNodeEndpoint],
    cluster: String,
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

  lazy val client: ElasticClient = ElasticClient(
    ElasticProperties(elasticNodeEndpoints,
                      Map("cluster.name" -> cluster, "client.transport.sniff" -> sniffCluster.toString)))
}

object ElasticWriteConfig {
  def apply(
      esNodeEndpoints: List[ElasticNodeEndpoint],
      esTargetCluster: String,
      esTargetIndexPrefix: String,
      esTargetType: String
  ): ElasticWriteConfig =
    new ElasticWriteConfig(esNodeEndpoints, esTargetCluster, esTargetIndexPrefix, esTargetType)
}
