package com.gladow.indexer4s.indexing_logic

import akka.stream.scaladsl.Source
import com.gladow.indexer4s.indexing_logic.IndexLogic.{CreateIndex, DeleteOldIndices, IndexSource, SwitchAlias}
import com.gladow.indexer4s.specs.Spec

class IndexableStreamSpec extends Spec {

  "The IndexableStream" should {
    "execute write commands in the right order" in {
      val interpreter = new TestInterpreter[String]

      new IndexableStream(Source.empty[String], interpreter)
        .run shouldBe a[Right[_, _]]

      interpreter.bufferOfActions should have size 2
      interpreter.bufferOfActions.head shouldBe a[CreateIndex.type]
      interpreter.bufferOfActions.tail.head shouldBe a[IndexSource[_]]
    }

    "execute write, delete commands in the right oder" in {
      val interpreter = new TestInterpreter[String]

      new IndexableStream(Source.empty[String], interpreter)
        .deleteOldIndices(0)
        .run shouldBe a[Right[_, _]]

      interpreter.bufferOfActions should have size 3
      interpreter.bufferOfActions.head shouldBe a[CreateIndex.type]
      interpreter.bufferOfActions.tail.head shouldBe a[IndexSource[_]]
      interpreter.bufferOfActions.tail.tail.head shouldBe a[DeleteOldIndices]
    }

    "execute write, switch commands in the right oder" in {
      val interpreter = new TestInterpreter[String]

      new IndexableStream(Source.empty[String], interpreter)
        .switchAliasFrom("alias", 0, 0)
        .run shouldBe a[Right[_, _]]

      interpreter.bufferOfActions should have size 3
      interpreter.bufferOfActions.head shouldBe a[CreateIndex.type]
      interpreter.bufferOfActions.tail.head shouldBe a[IndexSource[_]]
      interpreter.bufferOfActions.tail.tail.head shouldBe a[SwitchAlias]
    }

    "execute write, switch, delete commands in the right oder" in {
      val interpreter = new TestInterpreter[String]

      new IndexableStream(Source.empty[String], interpreter)
        .switchAliasFrom("alias", 0, 0)
        .deleteOldIndices(0)
        .run shouldBe a[Right[_, _]]

      interpreter.bufferOfActions should have size 4
      interpreter.bufferOfActions.head shouldBe a[CreateIndex.type]
      interpreter.bufferOfActions.tail.head shouldBe a[IndexSource[_]]
      interpreter.bufferOfActions.tail.tail.head shouldBe a[SwitchAlias]
      interpreter.bufferOfActions.tail.tail.tail.head shouldBe a[DeleteOldIndices]
    }

    "if a step fails return IndexError with succeeded steps and not execute following steps" in {
      val interpreter = new TestInterpreter[String]

      val runRes = new IndexableStream(Source.empty[String], interpreter)
        .switchAliasFrom("failAlias", 0, 0)
        .deleteOldIndices(0)
        .run

      runRes shouldBe a[Left[_, _]]
      runRes.left.value.succeededStages should have size 2

      interpreter.bufferOfActions should have size 3
      interpreter.bufferOfActions.head shouldBe a[CreateIndex.type]
      interpreter.bufferOfActions.tail.head shouldBe a[IndexSource[_]]
      interpreter.bufferOfActions.tail.tail.head shouldBe a[SwitchAlias]
    }
  }

}
