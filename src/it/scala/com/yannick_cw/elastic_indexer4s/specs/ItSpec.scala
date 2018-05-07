package com.yannick_cw.elastic_indexer4s.specs

import com.sksamuel.elastic4s.http.{RequestFailure, RequestSuccess}
import com.sksamuel.elastic4s.testkit.{ClassloaderLocalNodeProvider, ElasticMatchers, ElasticSugar}
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures

import scala.concurrent.{ExecutionContext, Future}

abstract class ItSpec
    extends AsyncWordSpecLike
    with Matchers
    with ScalaFutures
    with Inspectors
    with EitherValues
    with ClassloaderLocalNodeProvider
    with ElasticSugar
    with ElasticMatchers
    with CompleteLastly
    with RecoverMethods
    with BeforeAndAfterAll {
  implicit class TestExtractor[A](f: Future[Either[RequestFailure, RequestSuccess[A]]]) {
    def toResult: Future[A] =
      f.map(_.fold(err => throw new Exception("Failed with: " + err.error.rootCause.mkString("\n")), _.result))
  }
}
