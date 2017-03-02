package com.yannick_cw.indexer4s.elasticsearch.index_ops

import com.yannick_cw.indexer4s.Index_results.StageSucceeded
import com.yannick_cw.indexer4s.specs.AsyncSpec

import scala.concurrent.Future

class IndexDeletionSpec extends AsyncSpec {
  val newIndex = IndexWithInfo("newIndex", List("alias"), 99)

  "The IndexDeletion" should {
    "never delete the new index" in {
      val opsClient = testEsOpsClient(newIndex)
      val deleter = IndexDeletion(opsClient)

      deleter.deleteOldest("new", "newIndex", 0, false).map { deletionResult =>
        deletionResult.right.value shouldBe a [StageSucceeded]
        opsClient.deletedIndices shouldBe empty
      }
    }

    "never delete an index with alias if protected" in {
      val protectedIndices = (1 to 10).map(i => IndexWithInfo(s"index$i", List(s"alias$i"), i))
      val opsClient = testEsOpsClient(newIndex +: protectedIndices:_*)
      val deleter = IndexDeletion(opsClient)

      deleter.deleteOldest("inde", "index0", 0, true).map { deletionResult =>
        deletionResult.right.value shouldBe a [StageSucceeded]
        opsClient.deletedIndices shouldBe empty
      }
    }

    "only delete indices with the same prefix" in {
      val indicesWithSamePrefix = (1 to 10).map(i => IndexWithInfo(s"index$i", List(s"alias"), i))
      val differentIndices = (1 to 10).map(i => IndexWithInfo(s"some$i", List(s"alias"), i))
      val opsClient = testEsOpsClient(newIndex +: (indicesWithSamePrefix ++ differentIndices):_*)
      val deleter = IndexDeletion(opsClient)

      deleter.deleteOldest("inde", "index0", 0, false).map { deletionResult =>
        deletionResult.right.value shouldBe a [StageSucceeded]
        opsClient.deletedIndices should contain theSameElementsAs indicesWithSamePrefix.map(_.index)
      }
    }

    "delete the oldest indices first if more indices than defined to keep" in {
      val indices = scala.util.Random.shuffle((1 to 10).map(i => IndexWithInfo(s"index$i", List.empty, i)))
      val opsClient = testEsOpsClient(newIndex +: indices:_*)
      val deleter = IndexDeletion(opsClient)

      deleter.deleteOldest("inde", "newIndex", 5, false).map { deletionResult =>
        deletionResult.right.value shouldBe a [StageSucceeded]
        opsClient.deletedIndices should contain theSameElementsAs indices.sortBy(_.creationTime).take(5).map(_.index)
      }
    }
  }

  def testEsOpsClient(oldIndicesWithAlias: IndexWithInfo*) =
    new EsOpsClientApi {
      val deletedIndices = scala.collection.mutable.Buffer.empty[String]
      def delete(index: String): Future[_] = {
        deletedIndices += index
        Future.successful(())
      }
      def allIndicesWithAliasInfo: Future[List[IndexWithInfo]] = Future.successful(oldIndicesWithAlias.toList)
      def addAliasToIndex(index: String, alias: String): Future[_] = ???
      def sizeFor(index: String): Future[Long] = ???
      def removeAliasFromIndex(index: String, alias: String): Future[Unit] = ???
    }
}
