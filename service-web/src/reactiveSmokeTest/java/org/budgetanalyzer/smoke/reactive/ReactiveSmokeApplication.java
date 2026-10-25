package org.budgetanalyzer.smoke.reactive;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Minimal reactive consumer application for the isolated reactive smoke suite.
 *
 * <p>Scans only this fixture package. Service-common components arrive through Boot's normal
 * auto-configuration discovery.
 */
@SpringBootApplication
public class ReactiveSmokeApplication {}
