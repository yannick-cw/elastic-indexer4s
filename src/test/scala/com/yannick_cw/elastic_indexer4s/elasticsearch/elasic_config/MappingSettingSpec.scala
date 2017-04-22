package com.yannick_cw.elastic_indexer4s.elasticsearch.elasic_config

import com.yannick_cw.elastic_indexer4s.specs.Spec

class MappingSettingSpec extends Spec {

  "The MappingSetting" should {
    "be a ParsingFailure if it gets an invalid json" in {
      StringMappingSetting.unsafeString("not good : {{{") shouldBe a[Left[_, _]]
    }
    "be a MappingSetting if the json is valid" in {
      StringMappingSetting.unsafeString("""{ "test" : { "good" : 2} }""") shouldBe a[Right[_, MappingSetting]]
    }
  }
}
