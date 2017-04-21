package com.yannick_cw.elastic_indexer4s.elasticsearch.index_ops

import cats.implicits._
import com.sksamuel.elastic4s.ElasticDsl.{addAlias, removeAlias, search, _}
import com.sksamuel.elastic4s.TcpClient
import org.elasticsearch.action.admin.indices.alias.IndicesAliasesResponse
import org.elasticsearch.action.admin.indices.delete.DeleteIndexResponse

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

case class IndexWithInfo(index: String, aliases: List[String], creationTime: Long)

trait EsOpsClientApi {
  def removeAliasFromIndex(index: String, alias: String): Future[_]
  def addAliasToIndex(index: String, alias: String): Future[_]
  def sizeFor(index: String): Future[Long]
  def delete(index: String): Future[_]
  def allIndicesWithAliasInfo: Future[List[IndexWithInfo]]

  def indicesByAgeFor(alias: String): Future[List[String]] = for {
    indices <- allIndicesWithAliasInfo
  } yield indices.filter(_.aliases.contains(alias)).sortBy(_.creationTime).map(_.index)

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

  def delete(index: String): Future[DeleteIndexResponse] =
    client.execute(deleteIndex(index))

  //todo in terms of elastic4s
  def allIndicesWithAliasInfo: Future[List[IndexWithInfo]] = Future(
    client.java.admin().cluster().prepareState().execute().actionGet().getState.getMetaData.indices().asScala
      .map(c => IndexWithInfo(c.key, c.value.getAliases.asScala.map(_.key).toList, c.value.getCreationDate))
      .toList
  )

  def removeAliasFromIndex(index: String, alias: String): Future[IndicesAliasesResponse] =
    client.execute(removeAlias(alias) on index)

  def addAliasToIndex(index: String, alias: String): Future[IndicesAliasesResponse] =
    client.execute(addAlias(alias) on index)

  def sizeFor(index: String): Future[Long] =
    client.execute(search(index) size 0)
      .map(_.totalHits)
}

object EsOpsClient {
  def apply(client: TcpClient): EsOpsClient = new EsOpsClient(client)
}