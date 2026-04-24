package com.dlqrevive.api;

import com.dlqrevive.reader.DLQMessage;
import com.dlqrevive.reader.DLQReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/dlq")
public class DlqController {

    private static final Logger log = LoggerFactory.getLogger(DlqController.class);
    
    private final DLQReader dlqReader;
    private final com.dlqrevive.audit.AuditLogger auditLogger;

    public DlqController(DLQReader dlqReader, com.dlqrevive.audit.AuditLogger auditLogger) {
        this.dlqReader = dlqReader;
        this.auditLogger = auditLogger;
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
}
