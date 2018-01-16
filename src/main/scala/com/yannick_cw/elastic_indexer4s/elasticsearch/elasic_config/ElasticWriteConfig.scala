package com.yannick_cw.elastic_indexer4s.elasticsearch.elasic_config

import com.sksamuel.elastic4s.TcpClient
import org.elasticsearch.common.settings.Settings
import org.joda.time.DateTime

import scala.concurrent.duration.{FiniteDuration, _}

case class ElasticWriteConfig(
  hosts: List[String],
  port: Int,
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
  val indexName = indexPrefix + "_" + new DateTime().toString("yyyy-MM-dd't'HH:mm:ss")
  private def settings =
    Settings.builder().put("cluster.name", cluster).put("client.transport.sniff", sniffCluster).build()
  lazy val client: TcpClient = TcpClient.transport(settings, "elasticsearch://" + hosts
    .map(host => s"$host:$port").mkString(","))
}

object ElasticWriteConfig {
  def apply(
    esTargetHosts: List[String],
    esTargetPort: Int,
    esTargetCluster: String,
    esTargetIndexPrefix: String,
    esTargetType: String
  ): ElasticWriteConfig =
    new ElasticWriteConfig(esTargetHosts, esTargetPort, esTargetCluster, esTargetIndexPrefix, esTargetType)
}