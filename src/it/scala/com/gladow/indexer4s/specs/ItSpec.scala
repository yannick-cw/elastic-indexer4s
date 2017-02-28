package com.gladow.indexer4s.specs

import com.sksamuel.elastic4s.testkit.{ElasticMatchers, ElasticSugar}
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures

abstract class ItSpec
    extends AsyncWordSpecLike
    with Matchers
    with ScalaFutures
    with Inspectors
    with EitherValues
    with ElasticSugar
    with ElasticMatchers
    with CompleteLastly
