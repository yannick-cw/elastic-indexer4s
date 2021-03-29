package com.yannick_cw.elastic_indexer4s.indexing_logic

import akka.stream.scaladsl.Source
import cats.data.WriterT._
import com.yannick_cw.elastic_indexer4s.Index_results.StageSuccess
import com.yannick_cw.elastic_indexer4s.elasticsearch.EsAccess
import com.yannick_cw.elastic_indexer4s.indexing_logic.TestMonad.{W, r}
import com.yannick_cw.elastic_indexer4s.specs.Spec

class IndexLogicSpec extends Spec {

  implicit val interpreter: EsAccess[W] = EsTestInterpreter
  val indexer                           = new IndexingWithEs[W]
  val written                           = indexer.write(Source.empty[String])

  "The IndexLogic" should {
    "be able to create writing commands, based on index creation and index writing" in {
      val runResult = indexer.write(Source.empty[String]).value

      runResult.value shouldBe a[Right[_, _]]

      runResult.written should have size 2
      runResult.written.head shouldBe CreateIndex
      runResult.written.tail.head shouldBe IndexIt
    }

    "be able to add a switch command to writing command" in {
      val runResult = indexer.addSwitch(written, 0.0, 0.0, "alias").value

      runResult.value shouldBe a[Right[_, _]]

      runResult.written should have size 3
      runResult.written.head shouldBe CreateIndex
      runResult.written.tail.head shouldBe IndexIt
      runResult.written.tail.tail.head shouldBe Switch
    }

    "be able to add a delete command to writing command" in {
      val runResult = indexer.addDelete(written, 0, false).value
      runResult.value shouldBe a[Right[_, _]]

      runResult.written should have size 3
      runResult.written.head shouldBe CreateIndex
      runResult.written.tail.head shouldBe IndexIt
      runResult.written.tail.tail.head shouldBe Delete
    }

    "if failing switching still collect successful create and write" in {
      val runResult = indexer.addSwitch(written, 0.0, 0.0, "failAlias").value

      runResult.value shouldBe a[Left[_, _]]
      runResult.value.left.value.succeededStages shouldBe List(
        StageSuccess("create"),
        StageSuccess("index")
      )

      runResult.written should have size 3
      runResult.written.head shouldBe CreateIndex
      runResult.written.tail.head shouldBe IndexIt
      runResult.written.tail.tail.head shouldBe Switch
    }
  }
}
