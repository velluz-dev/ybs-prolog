/**
 * <b>Feature: data administration.</b>
 *
 * <p>Create and edit every table the demo reasons over - stops, routes and their
 * itineraries, the traffic prior, drivers and their monthly scores, buses and incidents -
 * then export the result as a {@code data.sql} that reproduces it exactly.
 *
 * <p>Two things make this work rather than merely appear to. Every write goes through
 * {@link com.ybs.demo.admin.NetworkReloader}, because the map, the planner and the
 * simulation read caches built at startup and would otherwise ignore new data until the
 * next restart. And {@link com.ybs.demo.admin.SeedExportService} closes the loop: H2 is in
 * memory, so without an export every edit would be lost on restart.
 *
 * <p><i>No AI topic of its own - this is how the data those features reason over gets in.</i>
 */
package com.ybs.demo.admin;
