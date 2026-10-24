package org.budgetanalyzer.smoke.servlet;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.responses.ApiResponse;

import org.budgetanalyzer.service.exception.BusinessException;
import org.budgetanalyzer.service.exception.ResourceNotFoundException;

/** Servlet smoke-test controller. */
@RestController
@RequestMapping("/api/smoke")
public class SmokeItemController {

  static final String EXISTING_ITEM_ID = "item_1";
  static final String EXPLICIT_NOT_FOUND_DESCRIPTION = "Smoke item does not exist";

  /**
   * Returns an item or throws a not-found exception.
   *
   * @param id item identifier
   * @return the item
   */
  @GetMapping("/items/{id}")
  @ApiResponse(responseCode = "200", description = "Smoke item found")
  @ApiResponse(responseCode = "404", description = EXPLICIT_NOT_FOUND_DESCRIPTION)
  public SmokeItemResponse getItem(@PathVariable("id") String id) {
    if (!EXISTING_ITEM_ID.equals(id)) {
      throw new ResourceNotFoundException("Smoke item not found: " + id);
    }
    return new SmokeItemResponse(id, "First item", null);
  }

  /**
   * Creates an item for callers with the write permission.
   *
   * @param request validated create request
   * @return the created item
   */
  @PostMapping("/items")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('smoke:write')")
  public SmokeItemResponse createItem(@Valid @RequestBody CreateSmokeItemRequest request) {
    return new SmokeItemResponse("item_2", request.name(), null);
  }

  /**
   * Always throws a business exception with an error code.
   *
   * @return never returns normally
   */
  @GetMapping("/business-error")
  public SmokeItemResponse businessError() {
    throw new BusinessException("Smoke business rule violated", "SMOKE_RULE");
  }
}
