package com.example.backend.statistics;

import java.util.List;
import com.example.backend.auth.SessionUser;
import com.example.backend.statistics.dto.response.*;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/statistics/me")
public class StatisticsController {
    private final StatisticsService statistics;
    public StatisticsController(StatisticsService statistics) { this.statistics = statistics; }
    @GetMapping
    public ResponseEntity<StatisticsOverviewResponse> overview(@AuthenticationPrincipal SessionUser user) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(statistics.overview(user.id()));
    }
    @GetMapping("/decks")
    public ResponseEntity<List<DeckMasteryResponse>> decks(@AuthenticationPrincipal SessionUser user) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(statistics.decks(user.id()));
    }
    @GetMapping("/decks/{deckId}")
    public ResponseEntity<MasteryReportResponse> report(@AuthenticationPrincipal SessionUser user, @PathVariable("deckId") Long deckId,
            @RequestParam(name = "stars", defaultValue = "0") int stars, @RequestParam(name = "sort", defaultValue = "strongest") String sort,
            @RequestParam(name = "page", defaultValue = "0") int page, @RequestParam(name = "size", defaultValue = "20") int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(statistics.report(user.id(), deckId, stars, sort, page, size));
    }
}
