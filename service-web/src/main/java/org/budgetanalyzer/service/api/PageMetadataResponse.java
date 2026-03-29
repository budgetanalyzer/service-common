package org.budgetanalyzer.service.api;

import java.util.Objects;

import org.springframework.data.domain.Page;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Stable pagination metadata returned alongside paged API content.
 *
 * <p>This response keeps the public JSON contract under Budget Analyzer control instead of exposing
 * Spring Data's internal page serialization directly.
 *
 * @param page zero-based current page index
 * @param size requested page size
 * @param numberOfElements number of items in the current page content
 * @param totalElements total number of matching records across all pages
 * @param totalPages total number of pages for the current query
 * @param first whether the current page is the first page
 * @param last whether the current page is the last page
 */
@JsonPropertyOrder({
  "page",
  "size",
  "numberOfElements",
  "totalElements",
  "totalPages",
  "first",
  "last"
})
@Schema(description = "Pagination metadata for a paged API response")
public record PageMetadataResponse(
    @Schema(
            description = "Zero-based current page index",
            example = "0",
            requiredMode = Schema.RequiredMode.REQUIRED)
        int page,
    @Schema(
            description = "Requested page size",
            example = "50",
            requiredMode = Schema.RequiredMode.REQUIRED)
        int size,
    @Schema(
            description = "Number of items included in the current page",
            example = "50",
            requiredMode = Schema.RequiredMode.REQUIRED)
        int numberOfElements,
    @Schema(
            description = "Total number of matching records across all pages",
            example = "12437",
            requiredMode = Schema.RequiredMode.REQUIRED)
        long totalElements,
    @Schema(
            description = "Total number of pages for the current query",
            example = "249",
            requiredMode = Schema.RequiredMode.REQUIRED)
        int totalPages,
    @Schema(
            description = "Whether this is the first page",
            example = "true",
            requiredMode = Schema.RequiredMode.REQUIRED)
        boolean first,
    @Schema(
            description = "Whether this is the last page",
            example = "false",
            requiredMode = Schema.RequiredMode.REQUIRED)
        boolean last) {

  /**
   * Creates pagination metadata from a Spring Data page.
   *
   * @param page the Spring Data page to convert
   * @return stable pagination metadata for the page
   */
  public static PageMetadataResponse from(Page<?> page) {
    Objects.requireNonNull(page, "page must not be null");

    return new PageMetadataResponse(
        page.getNumber(),
        page.getSize(),
        page.getNumberOfElements(),
        page.getTotalElements(),
        page.getTotalPages(),
        page.isFirst(),
        page.isLast());
  }
}
