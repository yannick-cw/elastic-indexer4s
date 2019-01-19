package com.yannick_cw.elastic_indexer4s.specs

import com.sksamuel.elastic4s.http.Response
import com.sksamuel.elastic4s.testkit.{ElasticMatchers, ElasticSugar}
import com.whisk.docker.impl.spotify.DockerKitSpotify
import com.whisk.docker.scalatest.DockerTestKit
import org.scalatest.{AsyncTestSuite, _}
import org.scalatest.concurrent.ScalaFutures

import scala.concurrent.Future

trait ItSpec
    extends Matchers
    with ScalaFutures
    with Inspectors
    with EitherValues
    with ElasticSugar
    with ElasticMatchers
    with IndexerElasticSearchService
    with DockerTestKit
    with DockerKitSpotify
    with CompleteLastly
    with RecoverMethods
    with BeforeAndAfterAll { this: Suite =>
  implicit class TestExtractor[A](f: Future[Response[A]]) {
    def toResult: Future[A] =
      f.map(res => res.fold(throw new Exception("Failed with: " + res.error.rootCause.mkString("\n")))(identity))
  }
}
