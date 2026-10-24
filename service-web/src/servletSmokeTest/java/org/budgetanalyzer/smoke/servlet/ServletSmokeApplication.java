package org.budgetanalyzer.smoke.servlet;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Minimal servlet consumer application for the isolated servlet smoke suite.
 *
 * <p>Scans only this fixture package. Service-common components arrive through Boot's normal
 * auto-configuration discovery.
 */
@SpringBootApplication
public class ServletSmokeApplication {}
