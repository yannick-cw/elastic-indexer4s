package com.yannick_cw.elastic_indexer4s

import com.sksamuel.elastic4s.indexes.CreateIndexRequest

package object elasticsearch {

  implicit class EnhancedCreateIndexDef(definition: CreateIndexRequest) {
    def shards(optShards: Option[Int]): CreateIndexRequest =
      optShards
        .fold(definition)(s => definition shards s)
    def replicas(optReplicas: Option[Int]): CreateIndexRequest =
      optReplicas
        .fold(definition)(s => definition replicas s)
  }
}
