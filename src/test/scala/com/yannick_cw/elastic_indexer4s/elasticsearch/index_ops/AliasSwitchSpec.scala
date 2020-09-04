package com.yannick_cw.elastic_indexer4s.elasticsearch.index_ops

import cats.data.EitherT
import com.yannick_cw.elastic_indexer4s.Index_results.IndexError
import com.yannick_cw.elastic_indexer4s.specs.AsyncSpec

import scala.concurrent.Future

class AliasSwitchSpec extends AsyncSpec {

  val newIndex      = "index"
  val oldIndexAlias = "alias"
  val oldIndexName  = "oldIndex"

  def switchAlias(oldSize: Int,
                  newSize: Int,
                  failForNew: Boolean,
                  expectSwitchFrom: String,
                  oldIndicesWithAlias: IndexWithInfo*) =
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
      switchAlias(10, 10, false, oldIndexName, IndexWithInfo(oldIndexName, List(oldIndexAlias), 1))
        .switchAlias(oldIndexAlias, newIndex)
        .map { switchResult =>
          switchResult.right.value shouldBe an[AliasSwitched]
        }
    }

    "not switch the alias if threshold was exceeded" in {
      switchAlias(5, 10, false, "noSwitch", IndexWithInfo(oldIndexName, List(oldIndexAlias), 1))
        .switchAlias(oldIndexAlias, newIndex)
        .map { switchResult =>
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

  def testEsOpsClient(oldSize: Int,
                      newSize: Int,
                      failForNew: Boolean,
                      expectSwitchFrom: String,
                      oldIndicesWithAlias: Seq[IndexWithInfo]): EsOpsClientApi =
    new EsOpsClientApi {
      def removeAliasFromIndex(index: String, alias: String): OpsResult[Boolean] = EitherT.pure[Future, IndexError] {
        index should be(expectSwitchFrom)
        true
      }
      def addAliasToIndex(index: String, alias: String): OpsResult[Boolean] = EitherT.pure[Future, IndexError](true)
      def sizeFor(index: String): OpsResult[Long] =
        EitherT.pure[Future, IndexError](index match {
          case "oldIndex" => oldSize.toLong
          case "index" =>
            if (failForNew) throw new IllegalArgumentException("fail")
            else newSize.toLong
        })
      def delete(index: String): OpsResult[Boolean] = EitherT.pure[Future, IndexError](true)
      def allIndicesWithAliasInfo: OpsResult[List[IndexWithInfo]] =
        EitherT.pure[Future, IndexError](oldIndicesWithAlias.toList)
    }
}
