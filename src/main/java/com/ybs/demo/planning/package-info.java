/**
 * <b>Feature: journey planning.</b>
 *
 * <p><b>AI topic: heuristic search.</b> A* over a graph whose nodes are (stop, route)
 * pairs, so that changing bus is an explicit, chargeable step. Returns a Pareto set over
 * (minutes, fare, transfers) rather than one weighted "best" answer, and reports the nodes
 * it expanded with their g, h and f values so the frontier can be watched growing.
 *
 * <p>The admissibility argument for the heuristic - and the night-time bug that broke it
 * once - is documented on {@link com.ybs.demo.planning.AStarPlanner}. Start there.
 */
package com.ybs.demo.planning;
