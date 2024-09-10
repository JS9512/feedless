package org.migor.feedless.repository

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class AbstractRepositoryEntityTest {

  @ParameterizedTest
  @CsvSource(
    value = [
      "a random body;; ",
      "a random body with #tag1 #tag-foo;; tag1, tag-foo",
      "a #tag1 #tag1 #tag-foo;; tag1, tag-foo",
    ],
    delimiterString = ";;")
    fun testExtractHashTags(body: String, tags: String?) {
      assertThat(extractHashTags(body)).isEqualTo(tags?.split(Regex(",\\s*")) ?: emptyList<String>())
    }
}
