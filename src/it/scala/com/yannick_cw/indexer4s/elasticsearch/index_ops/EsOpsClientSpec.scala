package com.yannick_cw.indexer4s.elasticsearch.index_ops

import cats.implicits._
import com.yannick_cw.indexer4s.specs.ItSpec
import com.sksamuel.elastic4s.Indexes
import org.scalatest.FutureOutcome

class EsOpsClientSpec extends ItSpec {

  val indices = (1 to 10).map(i => s"index_$i")

  // delete all indices between tests
  override def withFixture(test: NoArgAsyncTest): FutureOutcome = {
    //create 10 indices
    indices.foreach(ensureIndexExists)
    complete(test()) lastly Indexes.All.values.foreach(deleteIndex)
  }

  val opsClient = EsOpsClient(client)

  "The EsOpsClient" should {
    "be able to delete indices" in {
      for {
        _ <- opsClient.delete(indices.head)
        _ = blockUntilIndexNotExists(indices.head)
      } yield {
        doesIndexExists(indices.head) shouldBe false
        forAll(indices.tail)(index => doesIndexExists(index) shouldBe true)
      }
    }

    "be able to get the index size" in {
      for {
        size <- opsClient.sizeFor(indices.head)
      } yield size shouldBe 0
    }

    "be able to add an alias to an index" in {
      for {
        _ <- opsClient.addAliasToIndex(indices.head, "alias")
        indicesInfo <- opsClient.allIndicesWithAliasInfo
      } yield indicesInfo.find(_.index == indices.head).get
        .aliases shouldBe List("alias")
    }

    "be able to remove an alias from an index" in {
      for {
        _ <- opsClient.addAliasToIndex(indices.last, "alias")
        aliasAdded <- opsClient.allIndicesWithAliasInfo
        _ <- opsClient.removeAliasFromIndex(indices.last, "alias")
        aliasDeleted <- opsClient.allIndicesWithAliasInfo
      } yield {
        aliasAdded.find(_.index == indices.last).get.aliases shouldBe List("alias")
        aliasDeleted.find(_.aliases.nonEmpty) shouldBe None
      }
    }

    "be able to retrieve indexInfo" in {
      for {
        _ <- indices.toList.traverse(opsClient.addAliasToIndex(_, "alias"))
        _ <- indices.toList.traverse(i => opsClient.addAliasToIndex(i, s"alias_$i"))
        indexInfos <- opsClient.allIndicesWithAliasInfo
      } yield {
        indexInfos.sortBy(_.creationTime).map(_.index) shouldBe indices
        forAll(indexInfos)(info =>
          info.aliases should contain theSameElementsAs List("alias", s"alias_${info.index}")
        )
      }
    }
  }
}
