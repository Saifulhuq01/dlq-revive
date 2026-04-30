package com.dlqrevive.api;

import com.dlqrevive.reader.DLQMessage;
import com.dlqrevive.reader.DLQReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import com.dlqrevive.transform.JsonataEngine;
import com.dlqrevive.transform.TransformationException;

@RestController
@RequestMapping("/dlq")
@CrossOrigin(origins = "http://localhost:4200")
public class DlqController {
    private static final Logger log = LoggerFactory.getLogger(DlqController.class);
    
    private final DLQReader dlqReader;
    private final com.dlqrevive.audit.AuditLogger auditLogger;
    private final JsonataEngine jsonataEngine;

    public DlqController(DLQReader dlqReader, com.dlqrevive.audit.AuditLogger auditLogger, JsonataEngine jsonataEngine) {
        this.dlqReader = dlqReader;
        this.auditLogger = auditLogger;
        this.jsonataEngine = jsonataEngine;
    }

    @GetMapping("/{topic}/messages")
    public List<DLQMessage> getMessages(
            @PathVariable String topic,
            @RequestParam String bootstrapServers,
            // Defaulting to partition 0 because DLQs are often single-partitioned.
            // If the topic has multiple partitions, the frontend will need to loop or specify it.
            @RequestParam(defaultValue = "0") int partition,
            @RequestParam(defaultValue = "0") long fromOffset,
            @RequestParam(defaultValue = "10") int limit) {
        
        log.info("REST request to read messages from topic: {}, partition: {}, fromOffset: {}, limit: {}", 
                topic, partition, fromOffset, limit);
                
        // TODO: Replace "api-user" with actual authenticated user identity once Spring Security is added
        auditLogger.logBrowse(topic, partition, fromOffset, "api-user");
                
        return dlqReader.readMessages(bootstrapServers, topic, partition, fromOffset, limit);
    }

    @PostMapping("/transform/preview")
    public TransformPreviewResponse previewTransform(@RequestBody TransformPreviewRequest request) {
        log.info("REST request to preview transformation");
        
        try {
            String output = jsonataEngine.transform(request.sampleMessage(), request.expression());
            return new TransformPreviewResponse(request.sampleMessage(), output, true, null);
        } catch (Exception e) {
            log.debug("Transformation preview failed: {}", e.getMessage());
            return new TransformPreviewResponse(request.sampleMessage(), null, false, e.getMessage());
        }
    }

    public record TransformPreviewRequest(String expression, String sampleMessage) {}
    public record TransformPreviewResponse(String input, String output, boolean valid, String error) {}
}
