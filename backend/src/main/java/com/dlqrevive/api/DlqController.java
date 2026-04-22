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

    public DlqController(DLQReader dlqReader) {
        this.dlqReader = dlqReader;
    }

    @GetMapping("/{topic}/messages")
    public List<DLQMessage> getMessages(
            @PathVariable String topic,
            @RequestParam String bootstrapServers,
            @RequestParam(defaultValue = "0") int partition,
            @RequestParam(defaultValue = "0") long fromOffset,
            @RequestParam(defaultValue = "10") int limit) {
        
        log.info("REST request to read messages from topic: {}, partition: {}, fromOffset: {}, limit: {}", 
                topic, partition, fromOffset, limit);
                
        return dlqReader.readMessages(bootstrapServers, topic, partition, fromOffset, limit);
    }
}
