package com.yannick_cw.elastic_indexer4s.indexing_logic

import akka.stream.scaladsl.Source
import com.yannick_cw.elastic_indexer4s.Index_results.StageSuccess
import com.yannick_cw.elastic_indexer4s.indexing_logic.IndexLogic._
import com.yannick_cw.elastic_indexer4s.specs.Spec

class IndexLogicSpec extends Spec {

  "The IndexLogic" should {
    "be able to create writing commands, based on index creation and index writing" in {

      val interpreter = new TestInterpreter[String]
      IndexLogic.write(Source.empty[String])
          .value.foldMap(interpreter) shouldBe a[Right[_, _]]

      interpreter.bufferOfActions should have size 2
      interpreter.bufferOfActions.head shouldBe a[CreateIndex.type]
      interpreter.bufferOfActions.tail.head shouldBe a[IndexSource[_]]
    }

    "be able to add a switch command to writing command" in {
      val interpreter = new TestInterpreter[String]
      IndexLogic.addSwitch(IndexLogic.write(Source.empty[String]), 0.0, 0.0, "alias")
        .value.foldMap(interpreter) shouldBe a[Right[_, _]]

      interpreter.bufferOfActions should have size 3
      interpreter.bufferOfActions.head shouldBe a[CreateIndex.type]
      interpreter.bufferOfActions.tail.head shouldBe a[IndexSource[_]]
      interpreter.bufferOfActions.tail.tail.head shouldBe a[SwitchAlias]
    }

    "be able to add a delete command to writing command" in {
      val interpreter = new TestInterpreter[String]
      IndexLogic.addDelete(IndexLogic.write(Source.empty[String]), 0, false)
        .value.foldMap(interpreter) shouldBe a[Right[_, _]]

      interpreter.bufferOfActions should have size 3
      interpreter.bufferOfActions.head shouldBe a[CreateIndex.type]
      interpreter.bufferOfActions.tail.head shouldBe a[IndexSource[_]]
      interpreter.bufferOfActions.tail.tail.head shouldBe a[DeleteOldIndices]
    }

    "if failing switching still collect successful create and write" in {
      val interpreter = new TestInterpreter[String]
      val result = IndexLogic.addSwitch(IndexLogic.write(Source.empty[String]), 0.0, 0.0, "failAlias")
        .value.foldMap(interpreter)
      result shouldBe a[Left[_, _]]
      result.left.value.succeededStages shouldBe List(
        StageSuccess("create"),
        StageSuccess("index")
      )

      interpreter.bufferOfActions should have size 3
      interpreter.bufferOfActions.head shouldBe a[CreateIndex.type]
      interpreter.bufferOfActions.tail.head shouldBe a[IndexSource[_]]
      interpreter.bufferOfActions.tail.tail.head shouldBe a[SwitchAlias]
    }
  }

}
