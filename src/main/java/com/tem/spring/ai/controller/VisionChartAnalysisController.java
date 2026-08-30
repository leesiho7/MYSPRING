package com.tem.spring.ai.controller;

import com.tem.spring.ai.dto.VisionChartAnalysisRequest;
import com.tem.spring.ai.dto.VisionChartAnalysisResponse;
import com.tem.spring.ai.service.VisionChartAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class VisionChartAnalysisController {

    private final VisionChartAnalysisService visionService;

    @PostMapping("/vision-analyze")
    public ResponseEntity<VisionChartAnalysisResponse> analyzeChartImage(@RequestBody VisionChartAnalysisRequest req) {
        log.info("[VisionChartController] Received chart vision analysis request for: {}", req.getSymbol());
        VisionChartAnalysisResponse response = visionService.analyzeChartImage(req);
        return ResponseEntity.ok(response);
    }
}