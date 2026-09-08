package com.ybs.demo;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The MySQL and Postgres seeds must say the same thing.
 *
 * <p>{@code data-postgres.sql} is generated from {@code data.sql} by
 * {@code tools/seed/to_postgres.py}, because the two databases have no shared syntax for an
 * idempotent insert: MySQL writes {@code INSERT IGNORE}, Postgres writes
 * {@code ON CONFLICT DO NOTHING}, and nothing accepts both.
 *
 * <p>A generated file checked in beside its source is a drift waiting to happen - someone
 * adds a stop to {@code data.sql}, never runs the script, and the Postgres deploy quietly
 * carries different data from the one that was tested. This applies the same transformation
 * in memory and fails if the checked-in file disagrees, so the drift is caught here rather
 * than on a host at 2am.
 *
 * <p>Deliberately not a {@code @SpringBootTest}: it reads two files and compares strings, so
 * it needs no database and runs in milliseconds. It is also the only test in the suite that
 * still passes with no database available at all.
 */
class SeedFilesInSyncTest {

    private static final Path RESOURCES = Path.of("src", "main", "resources");
    private static final Path MYSQL = RESOURCES.resolve("data.sql");
    private static final Path POSTGRES = RESOURCES.resolve("data-postgres.sql");

    /** The same two edits tools/seed/to_postgres.py makes, and nothing else. */
    private static String toPostgres(String mysql) {
        String out = mysql.replaceAll("(?m)^INSERT IGNORE INTO ", "INSERT INTO ");
        return out.replaceAll("(?m);\\s*$", Matcher.quoteReplacement("\nON CONFLICT DO NOTHING;"));
    }

    /** Everything from the first statement on - the generated file carries its own header. */
    private static String body(String sql) {
        Matcher m = Pattern.compile("(?m)^INSERT INTO ").matcher(sql);
        assertTrue(m.find(), "no INSERT statements found - is this the right file?");
        return sql.substring(m.start()).replace("\r\n", "\n").strip();
    }

    @Test
    void thePostgresSeedMatchesTheMysqlOne() throws IOException {
        String mysql = Files.readString(MYSQL, StandardCharsets.UTF_8);
        String postgres = Files.readString(POSTGRES, StandardCharsets.UTF_8);

        assertEquals(body(toPostgres(mysql)), body(postgres),
                "data-postgres.sql is out of date. Run: python tools/seed/to_postgres.py");
    }

    @Test
    void eachSeedUsesOnlyItsOwnDialect() throws IOException {
        String mysql = Files.readString(MYSQL, StandardCharsets.UTF_8);
        String postgres = body(Files.readString(POSTGRES, StandardCharsets.UTF_8));

        assertTrue(mysql.contains("INSERT IGNORE"), "data.sql should be the MySQL seed");
        assertTrue(!postgres.contains("INSERT IGNORE"),
                "Postgres cannot parse INSERT IGNORE - it would fail on the first statement");

        // Every statement guarded, or a restart against a live database throws duplicate keys.
        long inserts = postgres.lines().filter(l -> l.startsWith("INSERT INTO ")).count();
        long guards = postgres.lines().filter(l -> l.startsWith("ON CONFLICT DO NOTHING;")).count();
        assertEquals(inserts, guards, "every INSERT needs its own ON CONFLICT DO NOTHING");
        assertTrue(inserts >= 9, "expected at least the nine seeded tables, found " + inserts);
    }
}
