package org.budgetanalyzer.core.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class QueryParamSanitizerTest {

  private final QueryParamSanitizer queryParamSanitizer = new QueryParamSanitizer();

  @Test
  void shouldMaskSensitiveQueryParams() {
    var result = queryParamSanitizer.sanitize("code=authcode123&state=csrfstate456");

    assertThat(result).isEqualTo("code=***&state=***");
  }

  @Test
  void shouldPreserveNonSensitiveParams() {
    var result = queryParamSanitizer.sanitize("page=1&size=10&search=john");

    assertThat(result).isEqualTo("page=1&size=10&search=john");
  }

  @Test
  void shouldHandleMixedSensitiveAndNonSensitiveParams() {
    var result = queryParamSanitizer.sanitize("page=1&code=authcode123&size=10&state=csrfstate456");

    assertThat(result).isEqualTo("page=1&code=***&size=10&state=***");
  }

  @Test
  void shouldNormalizeParamNamesWithUnderscores() {
    var result = queryParamSanitizer.sanitize("access_token=secret123&page=1");

    assertThat(result).isEqualTo("access_token=***&page=1");
  }

  @Test
  void shouldNormalizeParamNamesWithHyphens() {
    var result = queryParamSanitizer.sanitize("access-token=secret123&page=1");

    assertThat(result).isEqualTo("access-token=***&page=1");
  }

  @Test
  void shouldNormalizeCamelCaseParamNames() {
    var result = queryParamSanitizer.sanitize("accessToken=secret123&page=1");

    assertThat(result).isEqualTo("accessToken=***&page=1");
  }

  @Test
  void shouldReturnNullForNullInput() {
    assertThat(queryParamSanitizer.sanitize(null)).isNull();
  }

  @Test
  void shouldReturnEmptyForEmptyInput() {
    assertThat(queryParamSanitizer.sanitize("")).isEmpty();
  }

  @Test
  void shouldPassThroughNoValueParams() {
    var result = queryParamSanitizer.sanitize("flag&code=secret");

    assertThat(result).isEqualTo("flag&code=***");
  }

  @Test
  void shouldPassThroughNoEqualsSegments() {
    var result = queryParamSanitizer.sanitize("segment1&segment2");

    assertThat(result).isEqualTo("segment1&segment2");
  }

  @Test
  void shouldMaskCustomAdditionalParams() {
    var sanitizerWithCustom = new QueryParamSanitizer(List.of("custom_param", "mySecret"));

    var result = sanitizerWithCustom.sanitize("custom_param=val1&mySecret=val2&page=1&code=auth");

    assertThat(result).isEqualTo("custom_param=***&mySecret=***&page=1&code=***");
  }

  @Test
  void shouldMaskAllDefaultSensitiveParams() {
    var result =
        queryParamSanitizer.sanitize(
            "code=a&state=b&token=c&accessToken=d&refreshToken=e"
                + "&sessionId=f&sid=g&password=h&secret=i&credential=j");

    assertThat(result)
        .isEqualTo(
            "code=***&state=***&token=***&accessToken=***&refreshToken=***"
                + "&sessionId=***&sid=***&password=***&secret=***&credential=***");
  }

  @Test
  void shouldHandleParamWithEmptyValue() {
    var result = queryParamSanitizer.sanitize("code=&page=1");

    assertThat(result).isEqualTo("code=***&page=1");
  }

  @Test
  void shouldHandleParamWithEqualsInValue() {
    var result = queryParamSanitizer.sanitize("code=abc=def&page=1");

    assertThat(result).isEqualTo("code=***&page=1");
  }
}
