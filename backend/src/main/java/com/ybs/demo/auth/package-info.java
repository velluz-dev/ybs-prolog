/**
 * Authentication and authorisation.
 *
 * <p><b>No AI topic here.</b> This package exists because the role switcher in the sidebar
 * used to be a UI convenience with nothing behind it - the README said so out loud - and a
 * driver's monthly score and a data-entry screen are not things any visitor should reach.
 *
 * <p>The rule the whole package follows: <b>the rider is never asked to sign in.</b> Planning
 * a trip, reading the map, watching the buses and inspecting the A* trace and the rule
 * console are all public, because a passenger needing an account to look up a bus would be
 * a worse system. {@link com.ybs.demo.auth.SecurityConfig} lists those paths explicitly
 * rather than leaving them to a default.
 */
package com.ybs.demo.auth;
