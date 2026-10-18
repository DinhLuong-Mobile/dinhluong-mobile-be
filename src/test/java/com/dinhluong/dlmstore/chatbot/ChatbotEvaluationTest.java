package com.dinhluong.dlmstore.chatbot;

import com.dinhluong.dlmstore.chatbot.ai.*;
import com.dinhluong.dlmstore.chatbot.dto.ConversationPrincipal;
import com.dinhluong.dlmstore.chatbot.repository.ChatbotInteractionRepository;
import com.dinhluong.dlmstore.chatbot.service.ConversationMemoryService;
import com.dinhluong.dlmstore.config.ProductDataStartupRunner;
import com.fasterxml.jackson.databind.*;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Scripted NLU boundary, real router/parser/planner/security/JPA. This is NOT live NLP accuracy. */
@SpringBootTest(properties = {"chatbot.ai.max-requests-per-minute=10000"})
@ActiveProfiles("test")
@Import(ChatbotFixtures.class)
@AutoConfigureMockMvc(print=org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint.NONE)
@Transactional
class ChatbotEvaluationTest extends IsolatedChatbotDatabase {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired ChatbotInteractionRepository history;
    @Autowired ConversationMemoryService memories;
    @MockBean ProductDataStartupRunner productRunner;
    @MockBean(name = "initUsers") org.springframework.boot.CommandLineRunner initializer;
    @MockBean(answer = Answers.CALLS_REAL_METHODS) GeminiAiClient gemini;
    @MockBean(answer = Answers.CALLS_REAL_METHODS) OpenAiClient openai;
    @MockBean(answer = Answers.CALLS_REAL_METHODS) OllamaAiClient ollama;
    private JsonNode currentAction;
    private static final List<Map<String, Object>> REPORT = new CopyOnWriteArrayList<>();

    static Stream<JsonNode> scenarios() throws Exception {
        try (var resource = ChatbotEvaluationTest.class.getResourceAsStream("/chatbot/evaluation-scenarios.json")) {
            var array = new ObjectMapper().readTree(resource);
            List<JsonNode> result = new ArrayList<>(); array.forEach(result::add);
            assertTrue(result.size() >= 40);
            assertTrue(result.stream().filter(s -> s.path("turns").size() >= 2).count() >= 20);
            return result.stream();
        }
    }

    @BeforeEach void noRealProviderCalls() {
        org.mockito.stubbing.Answer<String> answer = invocation -> ((String)invocation.getArgument(0))
                .contains("QUY TẮC INTENT:") ? currentAction.toString() : "Em có thể hỗ trợ anh/chị xem sản phẩm và lịch sử tư vấn.";
        doAnswer(answer).when(gemini).generate(anyString());
        doAnswer(answer).when(openai).generate(anyString());
        doAnswer(answer).when(ollama).generate(anyString());
    }

    @ParameterizedTest(name = "{index}: deterministic scenario")
    @MethodSource("scenarios")
    void executeScenario(JsonNode scenario) throws Exception {
        String token = UUID.randomUUID().toString(), id = null;
        List<JsonNode> responses = new ArrayList<>();
        List<Map<String, Object>> turns = new ArrayList<>();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scenario", scenario.path("id").asText());
        result.put("mode", "DETERMINISTIC_PROVIDER_FIXTURE_NOT_LIVE");
        result.put("features", scenario.path("feature").asText()); result.put("turns", turns);
        try {
            for (var turn : scenario.path("turns")) {
                currentAction = turn.path("action");
                var body = mapper.createObjectNode().put("message", turn.path("input").asText()).put("model", "GEMINI");
                if (id != null) body.put("conversationId", id);
                var http = mvc.perform(post("/api/chatbot").servletPath("/api/chatbot")
                        .header("Origin", "https://localhost:5173").cookie(new Cookie("dlm_guest", token))
                        .contentType("application/json").content(body.toString())).andReturn().getResponse();
                var root = mapper.readTree(http.getContentAsString(StandardCharsets.UTF_8));
                assertEquals(200, http.getStatus(), () -> "HTTP error: " + root);
                var data = root.path("data"); id = data.path("conversationId").asText();
                var memory = memories.load(id, new ConversationPrincipal(null, token));
                var saved = history.findByConversationIdOrderByIdDesc(id, PageRequest.of(0, 1)).get(0);
                Map<String, Object> actual = new LinkedHashMap<>();
                actual.put("input", turn.path("input")); actual.put("expectedStructuredAction", currentAction);
                actual.put("expectedStateAndResponseConstraints", turn.path("expect"));
                actual.put("actualStructuredAction", Map.of("intent", saved.getIntent(), "topicAction", saved.getContextAction(),
                        "messageAction", saved.getMessageAction()));
                actual.put("actualState", memory.activeTopic() == null ? Map.of() : memory.activeTopic().context());
                actual.put("actualResponse", data); turns.add(actual);
                verifyConstraints(turn.path("expect"), currentAction, saved.getIntent(), data, memory, responses);
                responses.add(data);
            }
            result.put("status", "PASS"); result.put("failureReason", null);
        } catch (AssertionError | Exception e) {
            result.put("status", "FAIL"); result.put("failureReason", e.getMessage()); throw e;
        } finally { REPORT.add(result); }
    }

    private void verifyConstraints(JsonNode expected, JsonNode action, String actualIntent, JsonNode data,
                                   com.dinhluong.dlmstore.chatbot.dto.ConversationMemory memory, List<JsonNode> prior) {
        assertEquals(expected.path("intent").asText(action.path("intent").asText()), actualIntent);
        assertFalse(data.path("conversationId").asText().isBlank()); assertTrue(data.path("topicId").isNumber());
        if (expected.has("products")) assertEquals(expected.path("products").asInt(), data.path("products").size());
        if (expected.has("minProducts")) assertTrue(data.path("products").size() >= expected.path("minProducts").asInt());
        if (expected.has("clarification")) assertEquals(expected.path("clarification").asBoolean(), data.path("requiresClarification").asBoolean());
        if (expected.has("sameTopic")) assertEquals(prior.get(expected.path("sameTopic").asInt()).path("topicId"), data.path("topicId"));
        if (expected.has("differentTopic")) assertNotEquals(prior.get(expected.path("differentTopic").asInt()).path("topicId"), data.path("topicId"));
        if (expected.has("sameProducts")) assertEquals(ids(prior.get(expected.path("sameProducts").asInt())), ids(data));
        if (expected.has("subsetOf")) assertTrue(ids(prior.get(expected.path("subsetOf").asInt())).containsAll(ids(data)));
        if (expected.has("positionsFrom")) {
            var rule = expected.path("positionsFrom"); var source = ids(prior.get(rule.path("turn").asInt()));
            List<Long> resolved = new ArrayList<>(); rule.path("positions").forEach(p -> resolved.add(source.get(p.asInt()-1)));
            assertEquals(resolved, ids(data));
        }
        if (expected.path("ascendingPrice").asBoolean()) {
            double previous = -1;
            for (var product : data.path("products")) { assertTrue(product.path("price").asDouble() >= previous); previous = product.path("price").asDouble(); }
        }
        var context = mapper.valueToTree(memory.activeTopic().context());
        expected.path("filters").fields().forEachRemaining(e -> {
            var actual = context.path(e.getKey());
            if (e.getValue().isNumber() && actual.isNumber()) {
                assertEquals(0, e.getValue().decimalValue().compareTo(actual.decimalValue()));
            } else assertEquals(e.getValue(), actual);
        });
        expected.path("absentFilters").forEach(field -> assertFalse(context.has(field.asText())));
        assertEquals(ids(data).size(), new HashSet<>(ids(data)).size());
    }

    private static List<Long> ids(JsonNode response) {
        List<Long> ids = new ArrayList<>(); response.path("products").forEach(p -> ids.add(p.path("id").asLong())); return ids;
    }
    @AfterAll static void exportReport() throws Exception {
        Path path = Path.of("target", "chatbot-evaluation", "deterministic-results.json"); Files.createDirectories(path.getParent());
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(path.toFile(), REPORT);
    }
}
