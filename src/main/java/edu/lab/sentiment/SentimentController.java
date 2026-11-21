package edu.lab.sentiment;

import java.io.IOException;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/sentiment")
public class SentimentController {
    private final SentimentModel model;
    private final String podName;

    public SentimentController(@Value("${POD_NAME:local}") String podName) throws IOException {
        model = SentimentModel.train(new ClassPathResource("training.tsv").getInputStream());
        this.podName = podName;
    }

    @GetMapping
    public ResponseEntity<Map<String, String>> analyzeQuery(
            @RequestParam("text") String text) {
        return analyze(text);
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> analyzeBody(@RequestBody Map<String, String> request) {
        return analyze(request.get("text"));
    }

    private ResponseEntity<Map<String, String>> analyze(String text) {
        if (text == null || text.isBlank() || text.length() > 4000) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Text must contain 1 to 4000 characters and must not be blank.");
        }
        return ResponseEntity.ok()
                .header("X-Pod-Name", podName)
                .body(Map.of("sentiment", model.predict(text)));
    }
}
