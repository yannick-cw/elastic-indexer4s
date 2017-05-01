package com.yannick_cw.elastic_indexer4s.elasticsearch.elasic_config

import com.sksamuel.elastic4s.analyzers.AnalyzerDefinition
import com.sksamuel.elastic4s.mappings.MappingDefinition
import io.circe.{Json, ParsingFailure}
import io.circe.parser.parse

sealed trait MappingSetting {
  def fold[A](typed: TypedMappingSetting => A, unsafe: StringMappingSetting => A): A
}

case class TypedMappingSetting(
  analyzer: List[AnalyzerDefinition] = List.empty,
  mappings: List[MappingDefinition] = List.empty,
  shards: Option[Int] = None,
  replicas: Option[Int] = None
)
  extends MappingSetting {
  def fold[A](typed: (TypedMappingSetting) => A, unsafe: (StringMappingSetting) => A): A = typed(this)
}
sealed abstract case class StringMappingSetting(source: Json) extends MappingSetting {
  def fold[A](typed: (TypedMappingSetting) => A, unsafe: (StringMappingSetting) => A): A = unsafe(this)
}
object StringMappingSetting {
  def unsafeString(source: String): Either[ParsingFailure, MappingSetting] =
    parse(source).map(json => new StringMappingSetting(json){})
}
