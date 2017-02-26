package com.gladow.indexer4s.elasticsearch.index_ops

import com.gladow.indexer4s.IndexResults.IndexError
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{AsyncWordSpecLike, EitherValues, Inspectors, Matchers}

import scala.concurrent.Future

class SwitchAliasSpec extends AsyncWordSpecLike
  with Matchers
  with ScalaFutures
  with EitherValues {

  "SwitchAliasSpec" should {

    val newIndex: String = "index"
    val oldIndexAlias: String = "alias"
    val oldIndexName = "oldIndex"

    def switchAlias(oldSize: Int, newSize: Int, failForNew: Boolean, expectSwitchFrom: String, oldIndicesWithAlias: String*) = new SwitchAlias(
      esClient = new EsOpsClientApi {
        def indicesByAgeFor(alias: String): Future[List[String]] = Future.successful(oldIndicesWithAlias.sorted.toList)
        def addAliasToIndex(index: String, alias: String): Future[_] = Future.successful(Unit)
        def sizeFor(index: String): Future[Long] = index match {
          case "oldIndex" => Future.successful(oldSize)
          case "index" =>
            if(failForNew) Future.failed(throw new IllegalArgumentException("fail"))
            else Future.successful(newSize)
        }
        def removeAliasFromIndex(index: String, alias: String): Future[Unit] = {
          index should be(expectSwitchFrom)
          Future.successful(Unit)
        }
      },
      alias = oldIndexAlias,
      newIndexName = newIndex,
      waitForElastic = 0
    )

    "create a new alias if no old one was found" in {
      switchAlias(0, 10, false, "noSwitch").switchAlias.map { switchResult =>
        switchResult.right.value shouldBe a [NewAliasCreated]
      }
    }

    "switch the alias if the threshold was not exceeded" in {
      switchAlias(10, 10, false, oldIndexName, oldIndexName).switchAlias.map { switchResult =>
        switchResult.right.value shouldBe an [AliasSwitched]
      }
    }

    "do not switch the alias if threshold was exceeded" in {
      switchAlias(5, 10, false, "noSwitch", oldIndexName).switchAlias.map { switchResult =>
        switchResult.left.value shouldBe an [IndexError]
      }
    }

    "not switch if the new size could not be queried" in {
      switchAlias(10, 10, true, "noSwitch").switchAlias.map { switchResult =>
        switchResult.left.value shouldBe an [IndexError]
      }
    }

    "compare size with the latest and switch alias from the older" in {
      switchAlias(10, 10, false, "aOldestIndex", oldIndexName, "aOldestIndex").switchAlias.map { switchResult =>
        switchResult.right.value shouldBe an [AliasSwitched]
      }
    }

  }
}
