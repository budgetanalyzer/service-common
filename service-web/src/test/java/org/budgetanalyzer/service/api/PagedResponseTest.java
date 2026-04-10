package org.budgetanalyzer.service.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import com.fasterxml.jackson.databind.ObjectMapper;

/** Unit tests for {@link PagedResponse}. */
@DisplayName("PagedResponse Tests")
class PagedResponseTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  @DisplayName("Should create paged response for empty page")
  void shouldCreatePagedResponseForEmptyPage() {
    var page = Page.empty(PageRequest.of(0, 50));

    var response = PagedResponse.from(page);

    assertThat(response.content()).isEmpty();
    assertThat(response.metadata().page()).isEqualTo(0);
    assertThat(response.metadata().size()).isEqualTo(50);
    assertThat(response.metadata().numberOfElements()).isEqualTo(0);
    assertThat(response.metadata().totalElements()).isEqualTo(0L);
    assertThat(response.metadata().totalPages()).isEqualTo(0);
    assertThat(response.metadata().first()).isTrue();
    assertThat(response.metadata().last()).isTrue();
  }

  @Test
  @DisplayName("Should create paged response for single page result")
  void shouldCreatePagedResponseForSinglePageResult() {
    var page = new PageImpl<>(List.of("txn_1", "txn_2"), PageRequest.of(0, 10), 2);

    var response = PagedResponse.from(page);

    assertThat(response.content()).containsExactlyElementsOf(List.of("txn_1", "txn_2"));
    assertThat(response.metadata().page()).isEqualTo(0);
    assertThat(response.metadata().size()).isEqualTo(10);
    assertThat(response.metadata().numberOfElements()).isEqualTo(2);
    assertThat(response.metadata().totalElements()).isEqualTo(2L);
    assertThat(response.metadata().totalPages()).isEqualTo(1);
    assertThat(response.metadata().first()).isTrue();
    assertThat(response.metadata().last()).isTrue();
  }

  @Test
  @DisplayName("Should expose correct metadata for multi page result")
  void shouldExposeCorrectMetadataForMultiPageResult() {
    var page =
        new PageImpl<>(
            List.of("txn_6", "txn_7", "txn_8", "txn_9", "txn_10"), PageRequest.of(1, 5), 13);

    var response = PagedResponse.from(page);

    assertThat(response.metadata().page()).isEqualTo(1);
    assertThat(response.metadata().size()).isEqualTo(5);
    assertThat(response.metadata().numberOfElements()).isEqualTo(5);
    assertThat(response.metadata().totalElements()).isEqualTo(13L);
    assertThat(response.metadata().totalPages()).isEqualTo(3);
    assertThat(response.metadata().first()).isFalse();
    assertThat(response.metadata().last()).isFalse();
  }

  @Test
  @DisplayName("Should map page content when mapper is provided")
  void shouldMapPageContentWhenMapperIsProvided() {
    var page = new PageImpl<>(List.of(101, 102), PageRequest.of(0, 2), 2);

    var response = PagedResponse.from(page, value -> "txn_" + value);

    assertThat(response.content()).containsExactlyElementsOf(List.of("txn_101", "txn_102"));
    assertThat(response.metadata().numberOfElements()).isEqualTo(2);
  }

  @Test
  @DisplayName("Should serialize stable JSON response shape")
  void shouldSerializeStableJsonResponseShape() throws Exception {
    var page = new PageImpl<>(List.of("txn_1", "txn_2"), PageRequest.of(1, 2), 5);

    var response = PagedResponse.from(page);

    var json = objectMapper.writeValueAsString(response);

    assertThat(json)
        .isEqualTo(
            "{\"content\":[\"txn_1\",\"txn_2\"],"
                + "\"metadata\":{\"page\":1,\"size\":2,\"numberOfElements\":2,"
                + "\"totalElements\":5,\"totalPages\":3,\"first\":false,\"last\":false}}");
  }
}
