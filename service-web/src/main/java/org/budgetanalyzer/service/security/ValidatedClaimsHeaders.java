package org.budgetanalyzer.service.security;

import java.util.List;

record ValidatedClaimsHeaders(String userId, List<String> permissions, List<String> roles) {}
