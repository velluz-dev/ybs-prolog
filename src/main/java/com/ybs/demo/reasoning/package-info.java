/**
 * <b>Feature: symbolic reasoning.</b>
 *
 * <p><b>AI topics: knowledge representation &amp; reasoning, and constraint satisfaction.</b>
 * An in-JVM tuProlog engine loaded from {@code src/main/resources/rules/ybs.pl}, which is
 * where the actual reasoning lives - Java only asserts ground facts and asks goals.
 *
 * <p>{@link com.ybs.demo.reasoning.PrologService} is the generic bridge (load, assert,
 * solve, log every goal). {@link com.ybs.demo.reasoning.FactAssembler} holds everything
 * domain-specific about which facts get asserted.
 */
package com.ybs.demo.reasoning;
