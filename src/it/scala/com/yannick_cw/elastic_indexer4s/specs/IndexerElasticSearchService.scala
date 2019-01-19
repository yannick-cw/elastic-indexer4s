package com.yannick_cw.elastic_indexer4s.specs

import java.io.IOException
import java.net.ServerSocket

import com.sksamuel.elastic4s.http.{ElasticClient, ElasticProperties}
import com.sksamuel.elastic4s.testkit.ClientProvider
import com.whisk.docker.{DockerContainer, DockerKit, DockerPortMapping, DockerReadyChecker}

import scala.annotation.tailrec
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

trait IndexerElasticSearchService extends DockerKit with ClientProvider {
  val DefaultElasticsearchHttpPort   = 9200
  val DefaultElasticsearchClientPort = 9300
  val ElasticsearchHttpPort          = getFreePort
  val ElasticsearchClientPort        = getFreePort

  override val StartContainersTimeout = 60.seconds

  override def client: ElasticClient = ElasticClient(ElasticProperties(s"http://localhost:$ElasticsearchHttpPort"))

  val elasticsearchContainer: DockerContainer =
    DockerContainer("docker.elastic.co/elasticsearch/elasticsearch:6.4.0")
      .withPortMapping(
        DefaultElasticsearchHttpPort   -> DockerPortMapping(Some(ElasticsearchHttpPort)),
        DefaultElasticsearchClientPort -> DockerPortMapping(Some(ElasticsearchClientPort))
      )
      .withEnv("discovery.type=single-node",
               "http.host=0.0.0.0",
               "transport.host=127.0.0.1",
               "xpack.security.enabled=false")
      .withReadyChecker(
        DockerReadyChecker
          .HttpResponseCode(DefaultElasticsearchHttpPort, "/", Some("0.0.0.0"))
          .within(100.millis)
          .looped(20, 1250.millis))

  abstract override def dockerContainers: List[DockerContainer] =
    elasticsearchContainer :: super.dockerContainers

  @tailrec
  private final def getFreePort: Int = {
    Try(new ServerSocket(0)) match {
      case Success(socket) =>
        val port = socket.getLocalPort
        socket.close()
        port
      case Failure(_: IOException) => getFreePort
      case Failure(e)              => throw e
    }
  }
}
