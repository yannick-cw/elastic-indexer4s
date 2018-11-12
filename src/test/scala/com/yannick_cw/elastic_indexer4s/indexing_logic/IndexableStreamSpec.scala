package com.yannick_cw.elastic_indexer4s.indexing_logic

import akka.stream.scaladsl.Source
import cats.data.WriterT._
import cats.instances.list.catsKernelStdMonoidForList
import com.yannick_cw.elastic_indexer4s.indexing_logic.TestMonad.{W, r}
import com.yannick_cw.elastic_indexer4s.specs.Spec

class IndexableStreamSpec extends Spec {

  implicit val interpreter: IndexOps[W] = IndexOpsTestInterpreter
  val streamEmpty                       = new IndexableStream[String, W](Source.empty[String])

  "The IndexableStream" should {
    "execute write commands in the right order" in {
      val runResult = streamEmpty.run
      runResult.value shouldBe a[Right[_, _]]

      runResult.written should have size 1
      runResult.written.head shouldBe IndexIt
    }

    "execute write, delete commands in the right oder" in {
      val runResult = streamEmpty
        .deleteOldIndices(0)
        .run

      runResult.value shouldBe a[Right[_, _]]

      runResult.written should have size 2
      runResult.written.head shouldBe IndexIt
      runResult.written.last shouldBe Delete
    }

    "execute write, switch commands in the right oder" in {
      val runResult = streamEmpty
        .switchAliasFrom("alias", 0, 0)
        .run

      runResult.value shouldBe a[Right[_, _]]

      runResult.written should have size 2
      runResult.written.head shouldBe IndexIt
      runResult.written.last shouldBe Switch
    }

    "execute write, switch, delete commands in the right oder" in {
      val runResult = streamEmpty
        .switchAliasFrom("alias", 0, 0)
        .deleteOldIndices(0)
        .run

      runResult.value shouldBe a[Right[_, _]]

      runResult.written should have size 3
      runResult.written.head shouldBe IndexIt
      runResult.written.tail.head shouldBe Switch
      runResult.written.last shouldBe Delete
    }
  }
}
