package com.yannick_cw.elastic_indexer4s.elasticsearch.index_ops

import cats.data.EitherT
import cats.implicits._
import com.sksamuel.elastic4s.http.ElasticDsl.{addAlias, removeAlias, search, _}
import com.sksamuel.elastic4s.http.settings.IndexSettingsResponse
import com.sksamuel.elastic4s.http.{ElasticClient, Response}
import com.yannick_cw.elastic_indexer4s.Index_results.IndexError

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

case class IndexWithInfo(index: String, aliases: List[String], creationTime: Long)

trait EsOpsClientApi {

  type OpsResult[A] = EitherT[Future, IndexError, A]

  def removeAliasFromIndex(index: String, alias: String): OpsResult[Boolean]
  def addAliasToIndex(index: String, alias: String): OpsResult[Boolean]
  def sizeFor(index: String): OpsResult[Long]
  def delete(index: String): OpsResult[Boolean]
  def allIndicesWithAliasInfo: OpsResult[List[IndexWithInfo]]

  def indicesByAgeFor(alias: String): OpsResult[List[String]] =
    for {
      indices <- allIndicesWithAliasInfo
    } yield indices.filter(_.aliases.contains(alias)).sortBy(_.creationTime).map(_.index)

  def latestIndexWithAliasSize(alias: String): OpsResult[Option[Long]] =
    for {
      indices <- indicesByAgeFor(alias)
      size    <- indices.lastOption.traverse(sizeFor)
    } yield size

  def removeAliasFromOldestIfExists(alias: String): OpsResult[Option[Boolean]] =
    for {
      indices    <- indicesByAgeFor(alias)
      optRemoved <- indices.headOption.traverse(removeAliasFromIndex(_, alias))
    } yield optRemoved

  def switchAliasToIndex(alias: String, index: String): OpsResult[Option[Boolean]] =
    for {
      rSuccess <- removeAliasFromOldestIfExists(alias)
      aSuccess <- addAliasToIndex(index, alias)
    } yield rSuccess.map(_ && aSuccess)
}

class EsOpsClient(client: ElasticClient) extends EsOpsClientApi {

  implicit class WithEitherTResult[A](f: Future[Response[A]]) {
    def opsResult: OpsResult[A] =
      EitherT(
        f.map(response =>
          response.fold[Either[IndexError, A]](
            Left(IndexError(s"Index creation failed with error: ${response.error}")))(Right(_))))
    def opsResult[B](to: A => B): OpsResult[B] = opsResult.map(to)
  }

  def delete(index: String): OpsResult[Boolean] =
    client.execute(deleteIndex(index)).opsResult(_.acknowledged)

  private def indexCreationDate(indexName: String, response: IndexSettingsResponse): Option[Long] =
    for {
      indexSettings <- response.settings.get(indexName)
      creationDate  <- indexSettings.get("index.creation_date")
    } yield creationDate.toLong

  def allIndicesWithAliasInfo: OpsResult[List[IndexWithInfo]] =
    for {
      aliases  <- client.execute(getAliases()).opsResult
      settings <- client.execute(getSettings(aliases.mappings.keys.map(_.name))).opsResult
    } yield
      aliases.mappings
        .map {
          case (index, aliasi) =>
            indexCreationDate(index.name, settings).map(date =>
              IndexWithInfo(index.name, aliasi.toList.map(_.name), date))
        }
        .collect { case Some(x) => x }
        .toList

  def removeAliasFromIndex(index: String, alias: String): OpsResult[Boolean] =
    client.execute(removeAlias(alias) on index).opsResult(_.acknowledged)

  def addAliasToIndex(index: String, alias: String): OpsResult[Boolean] =
    client.execute(addAlias(alias) on index).opsResult(_.acknowledged)

  def sizeFor(index: String): OpsResult[Long] =
    client.execute(search(index) size 0).opsResult(_.totalHits)
}

object EsOpsClient {
  def apply(client: ElasticClient): EsOpsClient = new EsOpsClient(client)
}
