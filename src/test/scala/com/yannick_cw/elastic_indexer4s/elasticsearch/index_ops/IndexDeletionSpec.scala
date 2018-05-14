package com.yannick_cw.elastic_indexer4s.elasticsearch.index_ops

import cats.data.EitherT
import cats.implicits._
import com.yannick_cw.elastic_indexer4s.Index_results.{IndexError, StageSucceeded}
import com.yannick_cw.elastic_indexer4s.specs.AsyncSpec

import scala.concurrent.Future

class IndexDeletionSpec extends AsyncSpec {
  val newIndex = IndexWithInfo("newIndex", List("alias"), 99)

  "The IndexDeletion" should {
    "never delete the new index" in {
      val opsClient = testEsOpsClient(newIndex)
      val deleter   = IndexDeletion(opsClient)

      deleter.deleteOldest("new", "newIndex", 0, false).map { deletionResult =>
        deletionResult.right.value shouldBe a[StageSucceeded]
        opsClient.deletedIndices shouldBe empty
      }
    }

    "never delete an index with alias if protected" in {
      val protectedIndices = (1 to 10).map(i => IndexWithInfo(s"index$i", List(s"alias$i"), i))
      val opsClient        = testEsOpsClient(newIndex +: protectedIndices: _*)
      val deleter          = IndexDeletion(opsClient)

      deleter.deleteOldest("inde", "index0", 0, true).map { deletionResult =>
        deletionResult.right.value shouldBe a[StageSucceeded]
        opsClient.deletedIndices shouldBe empty
      }
    }

    "only delete indices with the same prefix" in {
      val indicesWithSamePrefix = (1 to 10).map(i => IndexWithInfo(s"index$i", List(s"alias"), i))
      val differentIndices      = (1 to 10).map(i => IndexWithInfo(s"some$i", List(s"alias"), i))
      val opsClient             = testEsOpsClient(newIndex +: (indicesWithSamePrefix ++ differentIndices): _*)
      val deleter               = IndexDeletion(opsClient)

      deleter.deleteOldest("inde", "index0", 0, false).map { deletionResult =>
        deletionResult.right.value shouldBe a[StageSucceeded]
        opsClient.deletedIndices should contain theSameElementsAs indicesWithSamePrefix.map(_.index)
      }
    }

    "keep at least defined amount of indices, even if there are newer indices with different prefix" in {
      val indicesWithSamePrefix = (1 to 10).map(i => IndexWithInfo(s"index$i", List(s"alias"), i))
      val differentIndices      = (11 to 20).map(i => IndexWithInfo(s"some$i", List(s"alias"), i))
      val opsClient             = testEsOpsClient(newIndex +: (indicesWithSamePrefix ++ differentIndices): _*)
      val deleter               = IndexDeletion(opsClient)

      deleter.deleteOldest("inde", "index0", 3, false).map { deletionResult =>
        deletionResult.right.value shouldBe a[StageSucceeded]
        opsClient.deletedIndices should have length 7
      }
    }

    "delete the oldest indices first if more indices than defined to keep" in {
      val indices   = scala.util.Random.shuffle((1 to 10).map(i => IndexWithInfo(s"index$i", List.empty, i)))
      val opsClient = testEsOpsClient(newIndex +: indices: _*)
      val deleter   = IndexDeletion(opsClient)

      deleter.deleteOldest("inde", "newIndex", 5, false).map { deletionResult =>
        deletionResult.right.value shouldBe a[StageSucceeded]
        opsClient.deletedIndices should contain theSameElementsAs indices.sortBy(_.creationTime).take(5).map(_.index)
      }
    }
  }

  private def testEsOpsClient(oldIndicesWithAlias: IndexWithInfo*) =
    new EsOpsClientApi {
      val deletedIndices = scala.collection.mutable.Buffer.empty[String]

      def removeAliasFromIndex(index: String, alias: String): OpsResult[Boolean] = ???
      def addAliasToIndex(index: String, alias: String): OpsResult[Boolean]      = ???
      def sizeFor(index: String): OpsResult[Long]                                = ???
      def delete(index: String): OpsResult[Boolean] = {
        deletedIndices += index
        EitherT.pure[Future, IndexError](true)
      }
      def allIndicesWithAliasInfo: OpsResult[List[IndexWithInfo]] =
        EitherT.pure[Future, IndexError](oldIndicesWithAlias.toList)
    }

}
