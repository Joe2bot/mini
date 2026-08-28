package com.minicoze.platform.trace;

import com.minicoze.platform.common.ApiException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/traces")
public class TraceController {
    private final TraceRepository traces; private final TraceSpanRepository spans;
    public TraceController(TraceRepository traces,TraceSpanRepository spans){this.traces=traces;this.spans=spans;}
    @GetMapping("/{traceId}") public TraceResponse get(@PathVariable UUID traceId){TraceEntity trace=traces.findById(traceId).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"TRACE_NOT_FOUND","Trace not found"));return TraceResponse.from(trace,spans.findByTraceIdOrderByStartedAt(traceId));}
}
