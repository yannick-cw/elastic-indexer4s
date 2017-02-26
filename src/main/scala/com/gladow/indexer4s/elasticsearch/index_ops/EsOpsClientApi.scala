package com.gladow.indexer4s.elasticsearch.index_ops

import cats.implicits._
import com.sksamuel.elastic4s.ElasticDsl.{addAlias, removeAlias, search, _}
import com.sksamuel.elastic4s.TcpClient
import org.elasticsearch.action.admin.indices.alias.IndicesAliasesResponse
import org.elasticsearch.action.admin.indices.delete.DeleteIndexResponse
import org.elasticsearch.transport.RemoteTransportException

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait EsOpsClientApi {
  def removeAliasFromIndex(index: String, alias: String): Future[_]
  def addAliasToIndex(index: String, alias: String): Future[_]
  def sizeFor(index: String): Future[Long]
  def indicesByAgeFor(alias: String): Future[List[String]]
  def delete(index: String): Future[_]
  def allIndicesWithAliasInfo: Future[List[(String, Boolean, Long)]]

  def latestIndexWithAliasSize(alias: String): Future[Option[Long]] = for {
    indices <- indicesByAgeFor(alias)
    size <- indices.lastOption.traverse(sizeFor)
  } yield size

  def removeAliasFromOldestIfExists(alias: String): Future[Option[_]] = for {
    indices <- indicesByAgeFor(alias)
    optRemoved <- indices.headOption.traverse(removeAliasFromIndex(_, alias))
  } yield optRemoved

  def switchAliasToIndex(alias: String, index: String): Future[Unit] = for {
    _ <- removeAliasFromOldestIfExists(alias)
    _ <- addAliasToIndex(index, alias)
  } yield ()
}

class EsOpsClient(client: TcpClient) extends EsOpsClientApi {

  def delete(index: String): Future[DeleteIndexResponse] = client.execute(
    deleteIndex(index)
  )

  def allIndicesWithAliasInfo: Future[List[(String, Boolean, Long)]] = Future(
    client.java.admin().cluster().prepareState().execute().actionGet().getState.getMetaData.indices().asScala
      .map(c => (c.key, c.value.getAliases.asScala.map(_.key).nonEmpty, c.value.getCreationDate))
      .toList
  )

  def removeAliasFromIndex(index: String, alias: String): Future[IndicesAliasesResponse] = client.execute(
    removeAlias(alias) on index
  )

  def addAliasToIndex(index: String, alias: String): Future[IndicesAliasesResponse] = client.execute(
    addAlias(alias) on index
  )

  def sizeFor(index: String): Future[Long] = client.execute(
      search(index) size 0
  ).map(_.totalHits)

  def indicesByAgeFor(alias: String): Future[List[String]] = client.execute(
    getSettings(alias)
  ).map(response =>
    response.getIndexToSettings.keysIt().asScala
      .map(index => (index, response.getSetting(index, "index.creation_date").toLong))
      .toList
      .sortBy(_._2)
      .map(_._1)
  ).recover { case _:RemoteTransportException => List.empty }
}