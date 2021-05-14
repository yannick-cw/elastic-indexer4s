package com.yannick_cw.elastic_indexer4s.indexing_logic

import akka.stream.scaladsl.Source
import cats.data.Writer
import cats.data.WriterT._
import com.yannick_cw.elastic_indexer4s.indexing_logic.TestMonad.{W, r}
import com.yannick_cw.elastic_indexer4s.specs.Spec

class IndexableStreamSpec extends Spec {

  implicit val interpreter: IndexOps[W] = IndexOpsTestInterpreter
  val streamEmpty                       = new IndexableStream[String, W](Source.empty[String], () => Writer.tell(List(Shutdown)))

  "The IndexableStream" should {
    "execute write commands in the right order" in {
      val runResult = streamEmpty.run
      runResult.value shouldBe a[Right[_, _]]

      runResult.written shouldBe List(IndexIt, Shutdown)
    }

    "execute write, delete commands in the right oder" in {
      val runResult = streamEmpty
        .deleteOldIndices(0)
        .run

      runResult.value shouldBe a[Right[_, _]]

      runResult.written shouldBe List(IndexIt, Delete, Shutdown)
    }

    "execute write, switch commands in the right oder" in {
      val runResult = streamEmpty
        .switchAliasFrom("alias", 0, 0)
        .run

      runResult.value shouldBe a[Right[_, _]]

      runResult.written shouldBe List(IndexIt, Switch, Shutdown)
    }

    "execute write, switch, delete commands in the right oder" in {
      val runResult = streamEmpty
        .switchAliasFrom("alias", 0, 0)
        .deleteOldIndices(0)
        .run

      runResult.value shouldBe a[Right[_, _]]

      runResult.written shouldBe List(IndexIt, Switch, Delete, Shutdown)
    }
  }
}
