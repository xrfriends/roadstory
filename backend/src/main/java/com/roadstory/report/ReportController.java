package com.roadstory.report;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/reports")
public class ReportController {
    @PostMapping("/content")
    public ResponseEntity<Void> report(@Valid @RequestBody ContentReportRequest request) {
        return ResponseEntity.accepted().build();
    }

    public record ContentReportRequest(@NotBlank String storyId, @NotBlank String reason,
                                       @Size(max = 2000) String details) {}
}
