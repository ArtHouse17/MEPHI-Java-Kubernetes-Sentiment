package edu.lab.sentiment;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class SentimentApiTest {
    @Autowired
    private MockMvc mvc;

    @ParameterizedTest
    @CsvSource({
            "hello, positive",
            "awful service, negative",
            "the meeting starts at noon, neutral",
            "отличный результат, positive",
            "ужасно плохое качество, negative",
            "встреча начинается в полдень, neutral",
            "EXCELLENT!!!, positive",
            "qwertyuiop, neutral",
            "12345, neutral"
    })
    void classifiesQueryText(String text, String expected) throws Exception {
        mvc.perform(get("/api/sentiment").param("text", text))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sentiment").value(expected))
                .andExpect(header().string("X-Pod-Name", "local"));
    }

    @Test
    void acceptsRussianJsonBody() throws Exception {
        mvc.perform(post("/api/sentiment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"отличный результат\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sentiment").value("positive"))
                .andExpect(header().string("X-Pod-Name", "local"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t\n", "\u2003"})
    void rejectsBlankQueryText(String text) throws Exception {
        mvc.perform(get("/api/sentiment").param("text", text))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsMissingQueryText() throws Exception {
        mvc.perform(get("/api/sentiment"))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"text\":null}", "{\"text\":\"\"}", "{\"text\":\"  \"}", "{bad json"})
    void rejectsInvalidJsonBody(String json) throws Exception {
        mvc.perform(post("/api/sentiment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());
    }

    @Test
    void acceptsMaximumLengthTextThroughBothEndpoints() throws Exception {
        String text = "a".repeat(4000);
        mvc.perform(get("/api/sentiment").param("text", text))
                .andExpect(status().isOk());
        mvc.perform(post("/api/sentiment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"" + text + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsOversizedTextThroughBothEndpoints() throws Exception {
        String text = "a".repeat(4001);
        mvc.perform(get("/api/sentiment").param("text", text))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/sentiment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"" + text + "\"}"))
                .andExpect(status().isBadRequest());
    }
}
