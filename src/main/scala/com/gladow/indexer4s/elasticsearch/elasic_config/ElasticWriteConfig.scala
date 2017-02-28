package com.gladow.indexer4s.elasticsearch.elasic_config

import com.sksamuel.elastic4s.TcpClient
import com.sksamuel.elastic4s.analyzers.AnalyzerDefinition
import com.sksamuel.elastic4s.mappings.MappingDefinition
import org.elasticsearch.common.settings.Settings
import org.joda.time.DateTime
import scala.concurrent.duration._

import scala.concurrent.duration.FiniteDuration

case class ElasticWriteConfig(
  esTargetHosts: List[String],
  esTargetPort: Int,
  esTargetCluster: String,
  esTargetIndexPrefix: String,
  esTargetType: String,
  esTargetShards: Option[Int] = None,
  esTargetReplicas: Option[Int] = None,
  esTargetAnalyzers: List[AnalyzerDefinition] = List.empty,
  esTargetMappings: List[MappingDefinition] = List.empty,
  esWriteBatchSize: Int = 50,
  esWriteConcurrentRequest: Int = 10,
  esWriteMaxAttempts: Int = 5,
  logWriteSpeedEvery: FiniteDuration = 1 minute
) {
  val indexName = esTargetIndexPrefix + "_" + new DateTime().toString("yyyy-MM-dd't'HH:mm:ss")
  private def settings = Settings.builder().put("cluster.name", esTargetCluster).build()
  lazy val client: TcpClient = TcpClient.transport(settings, "elasticsearch://" + esTargetHosts
    .map(host => s"$host:$esTargetPort").mkString(","))
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