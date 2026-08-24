/**
 * <b>Feature: bus simulation.</b>
 *
 * <p>Advances every bus along its route once a second, scaled by the traffic prior and a
 * dwell penalty for how full the bus is. Exposes a 1x/10x/60x speed multiplier so a bus can
 * be driven into the arrival geofence within seconds during a live demo.
 *
 * <p><i>Not an AI topic - it is the moving world the other features act on.</i>
 */
package com.ybs.demo.simulation;
