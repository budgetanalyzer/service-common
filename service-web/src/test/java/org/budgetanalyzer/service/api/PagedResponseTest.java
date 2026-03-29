package org.budgetanalyzer.service.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    assertTrue(response.content().isEmpty());
    assertEquals(0, response.metadata().page());
    assertEquals(50, response.metadata().size());
    assertEquals(0, response.metadata().numberOfElements());
    assertEquals(0L, response.metadata().totalElements());
    assertEquals(0, response.metadata().totalPages());
    assertTrue(response.metadata().first());
    assertTrue(response.metadata().last());
  }

  @Test
  @DisplayName("Should create paged response for single page result")
  void shouldCreatePagedResponseForSinglePageResult() {
    var page = new PageImpl<>(List.of("txn_1", "txn_2"), PageRequest.of(0, 10), 2);

    var response = PagedResponse.from(page);

    assertIterableEquals(List.of("txn_1", "txn_2"), response.content());
    assertEquals(0, response.metadata().page());
    assertEquals(10, response.metadata().size());
    assertEquals(2, response.metadata().numberOfElements());
    assertEquals(2L, response.metadata().totalElements());
    assertEquals(1, response.metadata().totalPages());
    assertTrue(response.metadata().first());
    assertTrue(response.metadata().last());
  }

  @Test
  @DisplayName("Should expose correct metadata for multi page result")
  void shouldExposeCorrectMetadataForMultiPageResult() {
    var page =
        new PageImpl<>(
            List.of("txn_6", "txn_7", "txn_8", "txn_9", "txn_10"), PageRequest.of(1, 5), 13);

    var response = PagedResponse.from(page);

    assertEquals(1, response.metadata().page());
    assertEquals(5, response.metadata().size());
    assertEquals(5, response.metadata().numberOfElements());
    assertEquals(13L, response.metadata().totalElements());
    assertEquals(3, response.metadata().totalPages());
    assertFalse(response.metadata().first());
    assertFalse(response.metadata().last());
  }

  @Test
  @DisplayName("Should map page content when mapper is provided")
  void shouldMapPageContentWhenMapperIsProvided() {
    var page = new PageImpl<>(List.of(101, 102), PageRequest.of(0, 2), 2);

    var response = PagedResponse.from(page, value -> "txn_" + value);

    assertIterableEquals(List.of("txn_101", "txn_102"), response.content());
    assertEquals(2, response.metadata().numberOfElements());
  }

  @Test
  @DisplayName("Should serialize stable JSON response shape")
  void shouldSerializeStableJsonResponseShape() throws Exception {
    var page = new PageImpl<>(List.of("txn_1", "txn_2"), PageRequest.of(1, 2), 5);

    var response = PagedResponse.from(page);

    var json = objectMapper.writeValueAsString(response);

    assertEquals(
        "{\"content\":[\"txn_1\",\"txn_2\"],"
            + "\"metadata\":{\"page\":1,\"size\":2,\"numberOfElements\":2,"
            + "\"totalElements\":5,\"totalPages\":3,\"first\":false,\"last\":false}}",
        json);
  }
}
