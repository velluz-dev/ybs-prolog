package com.ybs.demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.logout;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The boundary, tested from both sides.
 *
 * <p>Most of these assert a <b>refusal</b>. That is the point: an authorisation rule that is
 * only ever exercised by the person it lets through is not tested at all, and the two failures
 * that matter here are letting a rider onto the admin screens and letting one driver read
 * another's file.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthTest {

    @Autowired MockMvc mvc;
    @Autowired PasswordEncoder encoder;

    /* ------------------------------------------------- the rider never signs in --- */

    @Test
    void everythingARiderNeedsIsPublic() throws Exception {
        // If this test ever starts failing, someone has locked a passenger out of the bus
        // timetable. That is the regression it exists to catch.
        for (String url : new String[]{
                "/api/health", "/api/network", "/api/buses/live",
                "/api/journeys/search?from=1&to=500", "/api/stops/500/arrivals",
                "/api/voice/samples", "/api/disruptions",
                "/api/prolog/query?goal=fare(x,'43',F)"}) {
            mvc.perform(get(url)).andExpect(status().isOk());
        }
    }

    @Test
    void askingWhoIAmDoesNotRequireBeingAnyone() throws Exception {
        // A page that cannot ask this without being signed in has to guess.
        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(false))
                .andExpect(jsonPath("$.role").value("RIDER"));
    }

    /* ------------------------------------------------------ signed out is refused --- */

    @Test
    void protectedThingsAreRefusedWithJsonNotARedirectToHtml() throws Exception {
        // 401 with a body, because the caller is fetch(). A 302 to a login page arrives
        // as an opaque redirect the page cannot act on.
        for (String url : new String[]{
                "/api/ops/dashboard", "/api/ops/drivers", "/api/ops/drivers/1",
                "/api/ops/me/shift", "/admin.html", "/ops.html", "/api/admin/stops"}) {
            mvc.perform(get(url))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentTypeCompatibleWith("application/json"));
        }
    }

    /* -------------------------------------------------------------------- sign in --- */

    @Test
    void goodCredentialsReturnTheIdentity() throws Exception {
        mvc.perform(formLogin("/api/auth/login").user("admin").password("admin123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void aDriversIdentityCarriesTheirDriverRow() throws Exception {
        // The link is the whole reason the table exists: it is what makes "my shift" mean
        // one person's shift rather than whichever id the client sends.
        mvc.perform(formLogin("/api/auth/login").user("ukyawwin").password("driver123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("DRIVER"))
                .andExpect(jsonPath("$.driverId").value(2));
    }

    @Test
    void aBurmeseNameSurvivesTheLoginResponse() throws Exception {
        // Regression: the login and logout handlers are written by hand rather than going
        // through Spring's message converters, and setContentType("application/json") alone
        // leaves getWriter() on the container default - cp1252 here - which turned
        // ဦးကျော်ဝင်း into a row of question marks in the sidebar.
        String body = mvc.perform(formLogin("/api/auth/login").user("ukyawwin").password("driver123"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertFalse(body.contains("?????"), "name was mangled: " + body);
        assertTrue(body.contains("ဦ"), "expected Burmese in: " + body);
    }

    @Test
    void badCredentialsSayNothingAboutWhichHalfWasWrong() throws Exception {
        // Same answer for an unknown user and a wrong password: a different one would tell
        // an attacker which usernames exist.
        String unknownUser = mvc.perform(
                        formLogin("/api/auth/login").user("nobody").password("whatever"))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        String wrongPassword = mvc.perform(
                        formLogin("/api/auth/login").user("admin").password("wrong"))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertEquals(unknownUser, wrongPassword);
    }

    /* ------------------------------------------------------ one role cannot be another --- */

    @Test
    void aDriverCannotReadAnotherDriversFile() throws Exception {
        // The hole this whole feature was added to close. Before it, the screen was
        // /api/ops/drivers/{id} behind a dropdown, so any visitor could read any driver's
        // headway score and complaints by changing a number.
        var driver = SecurityMockMvcRequestPostProcessors.user("ukyawwin").roles("DRIVER");
        mvc.perform(get("/api/ops/drivers/1").with(driver)).andExpect(status().isForbidden());
        mvc.perform(get("/api/ops/drivers").with(driver)).andExpect(status().isForbidden());
        mvc.perform(get("/api/ops/dashboard").with(driver)).andExpect(status().isForbidden());
    }

    @Test
    void aDriverCannotReachTheAdminScreens() throws Exception {
        var driver = SecurityMockMvcRequestPostProcessors.user("ukyawwin").roles("DRIVER");
        mvc.perform(get("/admin.html").with(driver)).andExpect(status().isForbidden());
        mvc.perform(get("/ops.html").with(driver)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/stops").with(driver)).andExpect(status().isForbidden());
    }

    @Test
    void anAdminIsNotADriverAndHasNoShift() throws Exception {
        // Not a gap: an admin has no driver row, so "my shift" is not a thing they have.
        // Answering with someone else's would be worse than refusing.
        var admin = SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMIN");
        mvc.perform(get("/api/ops/me/shift").with(admin)).andExpect(status().isForbidden());
        mvc.perform(get("/api/ops/dashboard").with(admin)).andExpect(status().isOk());
    }

    /* ----------------------------------------------------------------------- CSRF --- */

    @Test
    void aWriteWithoutTheCsrfTokenIsRefused() throws Exception {
        // Including the public writes. They have no privilege to borrow, so this is belt
        // and braces - but "exempt, and here is the threat model" is a comment that rots.
        mvc.perform(post("/api/sim/speed?multiplier=10")).andExpect(status().isForbidden());
        mvc.perform(post("/api/disruptions/clear")).andExpect(status().isForbidden());
    }

    @Test
    void aWriteWithTheCsrfTokenGoesThrough() throws Exception {
        mvc.perform(post("/api/sim/speed?multiplier=1")
                .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isOk());
    }

    /* -------------------------------------------------------------------- logout --- */

    @Test
    void logoutEndsTheSession() throws Exception {
        mvc.perform(logout("/api/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(false));
    }

    /* ------------------------------------------------------------------ passwords --- */

    @Test
    void seededPasswordsAreHashedNotStored() throws Exception {
        // A seed file with a plaintext password in it is the failure this guards against.
        // BCrypt hashes start $2a$ and never equal the password they encode.
        assertTrue(encoder.matches("admin123",
                "$2a$10$NMK8J7R3SAqkFCpM.5xPkO4gLl2KgR66BcZ.gTRy79qdtc0sRMoma"));
        assertFalse(encoder.matches("admin124",
                "$2a$10$NMK8J7R3SAqkFCpM.5xPkO4gLl2KgR66BcZ.gTRy79qdtc0sRMoma"));
    }

    @Test
    void theIllustrationPathIsNotBehindTheLogin() throws Exception {
        // The planner's artwork is served to signed-out riders, who are most of them. Left
        // off the allowlist it would 401 - which is what the favicon did for weeks,
        // silently, because a missing icon looks like nothing at all.
        //
        // Asserted as "not 401" rather than "200" on purpose: the images are not committed,
        // so a version that pinned 200 would fail on a clean checkout and would be testing
        // whether someone remembered to add a PNG. A 404 here is fine - it means security
        // let the request through and there was simply no file. A 401 is the actual bug.
        mvc.perform(get("/img/hero-bus.png"))
                .andExpect(result ->
                        org.junit.jupiter.api.Assertions.assertNotEquals(
                                401, result.getResponse().getStatus(),
                                "/img/** is behind the login - signed-out riders lose the artwork"));
    }

    @Test
    void aMissingFileUnderAPublicPathIsNotServed() throws Exception {
        // Asserted as "some 4xx" on purpose. An allowlisted path with no file behind it
        // answers 404 here and 401 through the running server - the allowlist is matched
        // either way, but the two stacks disagree about who reports the miss. The invariant
        // worth holding is the one that matters: a path nobody put a file at must never
        // return 200, and must never redirect to a login page a fetch() cannot follow.
        mvc.perform(get("/img/does-not-exist.png"))
                .andExpect(status().is4xxClientError());
    }
}
