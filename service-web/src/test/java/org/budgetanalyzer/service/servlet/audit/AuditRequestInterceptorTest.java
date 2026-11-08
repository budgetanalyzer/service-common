package org.budgetanalyzer.service.servlet.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import tools.jackson.databind.json.JsonMapper;

import org.budgetanalyzer.service.audit.AuditLogger;
import org.budgetanalyzer.service.security.ClaimsHeaderAuthenticationToken;
import org.budgetanalyzer.service.servlet.http.CorrelationIdFilter;

class AuditRequestInterceptorTest {

  private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();
  private static final String ACTOR = "usr_audit_actor";
  private static final String CORRELATION_ID = "req_audit-correlation";

  private final ch.qos.logback.classic.Logger auditLogbackLogger =
      (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(AuditLogger.LOGGER_NAME);
  private final ListAppender<ILoggingEvent> listAppender = new ListAppender<>();

  private MockMvc mockMvc;
  private Level originalLevel;

  @BeforeEach
  void setUp() {
    originalLevel = auditLogbackLogger.getLevel();
    auditLogbackLogger.setLevel(Level.INFO);
    listAppender.start();
    auditLogbackLogger.addAppender(listAppender);

    mockMvc =
        MockMvcBuilders.standaloneSetup(new AuditTestController())
            .addInterceptors(new AuditRequestInterceptor(new AuditLogger()))
            .addFilters(new CorrelationIdFilter())
            .build();

    var authentication = ClaimsHeaderAuthenticationToken.authenticated(ACTOR, Set.of(), List.of());
    authentication.setDetails(
        Map.of("email", "profile@example.test", "displayName", "Private Person"));
    SecurityContextHolder.getContext().setAuthentication(authentication);
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
    auditLogbackLogger.detachAppender(listAppender);
    auditLogbackLogger.setLevel(originalLevel);
    listAppender.stop();
  }

  @Test
  void shouldEmitExactlyOneEventForEachMappedStateChangingRequest() throws Exception {
    for (var request : stateChangingRequests()) {
      mockMvc.perform(withCorrelationId(request)).andExpect(status().isAccepted());
    }

    assertThat(listAppender.list).hasSize(4);
    assertThat(listAppender.list).allMatch(event -> event.getLevel() == Level.INFO);
    assertThat(listAppender.list)
        .extracting(ILoggingEvent::getFormattedMessage)
        .allMatch(message -> message.contains("/audit/resources/{resourceId}"));
  }

  @Test
  void shouldUseOnlySafeStructuredRequestMetadata() throws Exception {
    var body = "{\"email\":\"body@example.test\",\"displayName\":\"Body Person\"}";

    mockMvc
        .perform(
            withCorrelationId(
                    post("/audit/resources/{resourceId}", "txn_123")
                        .queryParam("email", "query@example.test")
                        .queryParam("displayName", "Query Person"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isAccepted());

    assertThat(listAppender.list).singleElement();
    var event = listAppender.list.getFirst();
    var message = event.getFormattedMessage();
    var auditEvent = JSON_MAPPER.readTree(message);

    assertThat(auditEvent.propertyNames())
        .containsExactly(
            "timestamp", "actor", "operation", "resourceIdentifier", "outcome", "correlationId");
    assertThat(auditEvent.get("timestamp").asString()).isNotBlank();
    assertThat(auditEvent.get("actor").asString()).isEqualTo(ACTOR);
    assertThat(auditEvent.get("operation").asString())
        .isEqualTo("POST /audit/resources/{resourceId}");
    assertThat(auditEvent.get("resourceIdentifier").asString()).isEqualTo("txn_123");
    assertThat(auditEvent.get("outcome").asInt()).isEqualTo(202);
    assertThat(auditEvent.get("correlationId").asString()).isEqualTo(CORRELATION_ID);
    assertThat(message)
        .doesNotContain(
            body,
            "body@example.test",
            "Body Person",
            "query@example.test",
            "Query Person",
            "profile@example.test",
            "Private Person",
            "email=",
            "displayName=");
  }

  @Test
  void shouldNotEmitEventsForReadsOrUnmappedRequests() throws Exception {
    mockMvc
        .perform(withCorrelationId(get("/audit/resources/{resourceId}", "txn_123")))
        .andExpect(status().isOk());
    mockMvc.perform(withCorrelationId(post("/audit/missing"))).andExpect(status().isNotFound());

    assertThat(listAppender.list).isEmpty();
  }

  private static List<MockHttpServletRequestBuilder> stateChangingRequests() {
    return List.of(
        post("/audit/resources/{resourceId}", "txn_post"),
        put("/audit/resources/{resourceId}", "txn_put"),
        patch("/audit/resources/{resourceId}", "txn_patch"),
        delete("/audit/resources/{resourceId}", "txn_delete"));
  }

  private static MockHttpServletRequestBuilder withCorrelationId(
      MockHttpServletRequestBuilder request) {
    return request.header(CorrelationIdFilter.CORRELATION_ID_HEADER, CORRELATION_ID);
  }

  @RestController
  @RequestMapping("/audit/resources")
  private static class AuditTestController {

    @RequestMapping(
        path = "/{resourceId}",
        method = {RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE})
    @ResponseStatus(org.springframework.http.HttpStatus.ACCEPTED)
    void change(
        @PathVariable("resourceId") String resourceId,
        @RequestBody(required = false) String requestBody) {
      // The payload is intentionally accepted to prove it never enters the audit event.
    }

    @GetMapping("/{resourceId}")
    void read(@PathVariable("resourceId") String resourceId) {}
  }
}
