package com.yannick_cw.elastic_indexer4s.elasticsearch.index_ops

import com.yannick_cw.elastic_indexer4s.Index_results.IndexError
import com.yannick_cw.elastic_indexer4s.specs.AsyncSpec

import scala.concurrent.Future

class AliasSwitchSpec extends AsyncSpec {

  val newIndex = "index"
  val oldIndexAlias = "alias"
  val oldIndexName = "oldIndex"

  def switchAlias(oldSize: Int, newSize: Int, failForNew: Boolean, expectSwitchFrom: String, oldIndicesWithAlias: IndexWithInfo*) =
    AliasSwitching(
      esClient = testEsOpsClient(oldSize, newSize, failForNew, expectSwitchFrom, oldIndicesWithAlias),
      minThreshold = 0.95,
      maxThreshold = 1.25,
      waitForElastic = 0
    )

  "AliasSwitching" should {
    "create a new alias if no old one was found" in {
      switchAlias(0, 10, false, "noSwitch").switchAlias(oldIndexAlias, newIndex).map { switchResult =>
        switchResult.right.value shouldBe a[NewAliasCreated]
      }
    }

    "switch the alias if the threshold was not exceeded" in {
      switchAlias(10, 10, false, oldIndexName, IndexWithInfo(oldIndexName, List(oldIndexAlias), 1)).switchAlias(oldIndexAlias, newIndex).map { switchResult =>
        switchResult.right.value shouldBe an[AliasSwitched]
      }
    }

    "not switch the alias if threshold was exceeded" in {
      switchAlias(5, 10, false, "noSwitch", IndexWithInfo(oldIndexName, List(oldIndexAlias), 1)).switchAlias(oldIndexAlias, newIndex).map { switchResult =>
        switchResult.left.value shouldBe an[IndexError]
      }
    }

    "not switch if the new size could not be queried" in {
      switchAlias(10, 10, true, "noSwitch").switchAlias(oldIndexAlias, newIndex).map { switchResult =>
        switchResult.left.value shouldBe an[IndexError]
      }
    }

    "compare size with the latest and switch alias from the oldest" in {
      switchAlias(
        10,
        10,
        false,
        "aOldestIndex",
        IndexWithInfo("aOldestIndex", List(oldIndexAlias), 1),
        IndexWithInfo("oldIndex", List(oldIndexAlias), 2)
      ).switchAlias(oldIndexAlias, newIndex).map { switchResult =>
        switchResult.right.value shouldBe an[AliasSwitched]
      }
    }

  }

  def testEsOpsClient(oldSize: Int, newSize: Int, failForNew: Boolean, expectSwitchFrom: String, oldIndicesWithAlias: Seq[IndexWithInfo]) =
    new EsOpsClientApi {
      def delete(index: String): Future[_] = Future.successful(())
      def allIndicesWithAliasInfo: Future[List[IndexWithInfo]] = Future.successful(oldIndicesWithAlias.toList)
      def addAliasToIndex(index: String, alias: String): Future[_] = Future.successful(Unit)
      def sizeFor(index: String): Future[Long] = index match {
        case "oldIndex" => Future.successful(oldSize)
        case "index" =>
          if (failForNew) Future.failed(throw new IllegalArgumentException("fail"))
          else Future.successful(newSize)
      }
      def removeAliasFromIndex(index: String, alias: String): Future[Unit] = {
        index should be(expectSwitchFrom)
        Future.successful(Unit)
      }
    }
}
