/**
 * <b>Feature: operations dashboard.</b>
 *
 * <p>Driver scores, one bunching alert, and incident counts. The bonus-eligibility badge is
 * the reason this feature exists: it is computed per driver by asking the Prolog engine at
 * request time, and the seeded database column is {@code false} for everyone so a dead rule
 * engine would be visible rather than hidden behind stale data.
 */
package com.ybs.demo.ops;
