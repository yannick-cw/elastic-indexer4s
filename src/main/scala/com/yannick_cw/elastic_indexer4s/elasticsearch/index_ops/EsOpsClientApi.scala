package com.yannick_cw.elastic_indexer4s.elasticsearch.index_ops

import cats.implicits._
import com.sksamuel.elastic4s.http.ElasticDsl.{addAlias, removeAlias, search, _}
import com.sksamuel.elastic4s.http.index.admin.{AliasActionResponse, DeleteIndexResponse}
import com.sksamuel.elastic4s.http.settings.IndexSettingsResponse
import com.sksamuel.elastic4s.http.{HttpClient, RequestFailure, RequestSuccess}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Try

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

class EsOpsClient(client: HttpClient) extends EsOpsClientApi {

  def throwErr[A](res: Either[RequestFailure, RequestSuccess[A]]): A =
    res.fold(err => throw new Exception("Failed with: " + err.error.rootCause.mkString("\n")), _.result)

  def delete(index: String): Future[DeleteIndexResponse] =
    {
      val a = client.execute(deleteIndex(index)).map(throwErr)
      a.onComplete(x => println("deleted: " + x))
      a
    }

  private def indexCreationDate(indexName: String, response: IndexSettingsResponse): Option[Long] =
    for {
      indexSettings  <- response.settings.get(indexName)
      creationDate <- indexSettings.get("index.creation_date")
    } yield creationDate.toLong

  def allIndicesWithAliasInfo: Future[List[IndexWithInfo]] =
    for {
      aliases  <- client.execute(getAliases()).map(throwErr)
      settings <- client.execute(getSettings(aliases.mappings.keys.map(_.name))).map(throwErr)
    } yield
      aliases.mappings
      .map {
             case (index, aliasi) =>
               indexCreationDate(index.name, settings).map(date =>
                                                             IndexWithInfo(index.name, aliasi.toList.map(_.name), date))
           }
      .collect { case Some(x) => x }
      .toList

  def removeAliasFromIndex(index: String, alias: String): Future[AliasActionResponse] =
    client.execute(removeAlias(alias) on index).map(throwErr)

  def addAliasToIndex(index: String, alias: String): Future[AliasActionResponse] =
    client.execute(addAlias(alias) on index).map(throwErr)

  def sizeFor(index: String): Future[Long] =
    client.execute(search(index) size 0).map(throwErr)
      .map(_.totalHits)
}

object EsOpsClient {
  def apply(client: HttpClient): EsOpsClient = new EsOpsClient(client)
}