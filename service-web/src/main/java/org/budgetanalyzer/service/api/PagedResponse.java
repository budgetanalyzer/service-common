package org.budgetanalyzer.service.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import org.springframework.data.domain.Page;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Stable paged API response wrapper for list and search endpoints.
 *
 * <p>This wrapper keeps the public JSON contract stable while allowing services to keep using
 * Spring Data's {@link Page} support internally for paging, sorting, and count queries.
 *
 * @param content the items included in the current page
 * @param metadata pagination metadata describing the current page and overall result set
 * @param <T> the response item type
 */
@JsonPropertyOrder({"content", "metadata"})
@Schema(description = "Stable paged API response wrapper")
public record PagedResponse<T>(
    @Schema(
            description = "Items included in the current page",
            requiredMode = Schema.RequiredMode.REQUIRED)
        List<T> content,
    @Schema(
            description = "Pagination metadata for the current result set",
            requiredMode = Schema.RequiredMode.REQUIRED)
        PageMetadataResponse metadata) {

  /**
   * Creates an immutable paged response.
   *
   * @param content the items included in the current page
   * @param metadata pagination metadata describing the current page and overall result set
   */
  public PagedResponse {
    Objects.requireNonNull(content, "content must not be null");
    Objects.requireNonNull(metadata, "metadata must not be null");

    content = Collections.unmodifiableList(new ArrayList<>(content));
  }

  /**
   * Creates a paged response from a Spring Data page without remapping the content type.
   *
   * @param page the Spring Data page to convert
   * @param <T> the page content type
   * @return a stable paged response
   */
  public static <T> PagedResponse<T> from(Page<T> page) {
    Objects.requireNonNull(page, "page must not be null");

    return new PagedResponse<>(page.getContent(), PageMetadataResponse.from(page));
  }

  /**
   * Creates a paged response from a Spring Data page by mapping the page content.
   *
   * @param page the Spring Data page to convert
   * @param mapper the mapper used to convert each page item to the response type
   * @param <S> the source page content type
   * @param <T> the target response content type
   * @return a stable paged response
   */
  public static <S, T> PagedResponse<T> from(
      Page<S> page, Function<? super S, ? extends T> mapper) {
    Objects.requireNonNull(page, "page must not be null");
    Objects.requireNonNull(mapper, "mapper must not be null");

    return from(page.map(mapper));
  }
}
