/**
 * <b>Feature: disruption injection.</b>
 *
 * <p>Flooding a stop asserts {@code flooded(sNN)} into the rule base, and journeys using it
 * are withdrawn with a named reason. Nothing in the planner or the UI is told what a flood
 * means - only {@code ybs.pl} is, which is the whole point of the demo moment.
 *
 * <p>Kept separate from the seeded incident log in {@code ops}: treating every historical
 * flood as still active would rule out every journey at startup.
 */
package com.ybs.demo.disruption;
