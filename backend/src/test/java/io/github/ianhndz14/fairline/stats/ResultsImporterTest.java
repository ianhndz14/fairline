package io.github.ianhndz14.fairline.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.ianhndz14.fairline.stats.ResultsImporter.MatchResult;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResultsImporterTest {

    // Trimmed copy of the real file's layout: BOM, extra columns, an unplayed row and a blank row.
    private static final String CSV = """
            ﻿Div,Date,Time,HomeTeam,AwayTeam,FTHG,FTAG,FTR,Referee
            E0,21/08/2026,20:00,Arsenal,Coventry,3,0,H,T Bramall
            E0,26/12/2026,15:00,Man United,Nott'm Forest,1,1,D,M Oliver
            E0,02/05/2027,16:30,Leeds,Hull,,,,
            ,,,,,,,,
            """;

    @Test
    void parsesPlayedMatchesOnly() {
        List<MatchResult> results = ResultsImporter.parse(CSV);
        assertEquals(2, results.size());
        assertEquals(new MatchResult("Arsenal", "Coventry", Instant.parse("2026-08-21T19:00:00Z"), 3, 0),
                results.get(0));
    }

    @Test
    void mapsTeamNamesToKalshiNames() {
        MatchResult draw = ResultsImporter.parse(CSV).get(1);
        assertEquals("Manchester United", draw.homeTeam());
        assertEquals("Nottingham Forest", draw.awayTeam());
    }

    @Test
    void convertsUkLocalTimeToUtc() {
        // 20:00 in August is British Summer Time (UTC+1); 15:00 in December is GMT (UTC+0).
        List<MatchResult> results = ResultsImporter.parse(CSV);
        assertEquals(Instant.parse("2026-08-21T19:00:00Z"), results.get(0).kickoff());
        assertEquals(Instant.parse("2026-12-26T15:00:00Z"), results.get(1).kickoff());
    }
}
