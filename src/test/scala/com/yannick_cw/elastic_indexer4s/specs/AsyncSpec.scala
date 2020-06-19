package com.yannick_cw.elastic_indexer4s.specs

import org.scalatest.concurrent.ScalaFutures
import org.scalatest._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpecLike

abstract class AsyncSpec
    extends AsyncWordSpecLike
    with Matchers
    with ScalaFutures
    with Inspectors
    with EitherValues
    with BeforeAndAfterAll
