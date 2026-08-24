/**
 * <b>Feature: the bus network.</b>
 *
 * <p>The stops, routes and running order that every other feature is defined against:
 * 28 stops with OpenStreetMap-verified coordinates and 3 route itineraries. Read once at
 * startup by {@link com.ybs.demo.network.NetworkService} and held in memory, because the
 * 1 Hz simulation tick and the A* search both consult it far more often than it changes.
 *
 * <p><i>No AI topic of its own - this is the world the AI features reason about.</i>
 */
package com.ybs.demo.network;
