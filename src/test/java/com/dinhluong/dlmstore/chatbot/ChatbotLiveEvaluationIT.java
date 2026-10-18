package com.dinhluong.dlmstore.chatbot;

import com.dinhluong.dlmstore.chatbot.ai.*;
import com.dinhluong.dlmstore.chatbot.dto.ConversationPrincipal;
import com.dinhluong.dlmstore.chatbot.exception.AiProviderException;
import com.dinhluong.dlmstore.chatbot.repository.ChatbotInteractionRepository;
import com.dinhluong.dlmstore.chatbot.service.ConversationMemoryService;
import com.dinhluong.dlmstore.config.ProductDataStartupRunner;
import com.fasterxml.jackson.databind.*;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.*;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Opt-in only. Not included by Surefire's default *Test naming, and additionally environment-gated. */
@EnabledIfEnvironmentVariable(named = "DLMSTORE_RUN_LIVE_EVAL", matches = "true")
@SpringBootTest
@ActiveProfiles("test")
@Import(ChatbotFixtures.class)
@AutoConfigureMockMvc(print=org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint.NONE)
@Transactional
class ChatbotLiveEvaluationIT extends IsolatedChatbotDatabase {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired ConversationMemoryService memory;
    @Autowired ChatbotInteractionRepository history;
    @MockBean ProductDataStartupRunner productRunner;
    @MockBean(name = "initUsers") org.springframework.boot.CommandLineRunner initializer;
    @SpyBean GeminiAiClient gemini;
    @SpyBean OpenAiClient openai;
    @SpyBean OllamaAiClient ollama;

    @Test void evaluateWithinExplicitBudget() throws Exception {
        int budget = Integer.parseInt(System.getenv().getOrDefault("DLMSTORE_LIVE_EVAL_MAX_CALLS", "0"));
        if (budget < 1 || budget > 100) throw new IllegalArgumentException("Live provider-call budget must be explicitly set to 1..100");
        String model = System.getenv().getOrDefault("DLMSTORE_LIVE_EVAL_MODEL", "OLLAMA");
        if (!Set.of("GEMINI","OPENAI","OLLAMA").contains(model)) throw new IllegalArgumentException("Select one explicit model, not AUTO");
        AtomicInteger charged = new AtomicInteger();
        // Gemini can rotate at most two keys: charge the worst-case cost before entering the client.
        doAnswer(i -> { charge(charged,budget,2); return i.callRealMethod(); }).when(gemini).generate(anyString());
        doAnswer(i -> { charge(charged,budget,1); return i.callRealMethod(); }).when(openai).generate(anyString());
        doAnswer(i -> { charge(charged,budget,1); return i.callRealMethod(); }).when(ollama).generate(anyString());
        List<Map<String,Object>> report = new ArrayList<>();
        var scenarios = mapper.readTree(getClass().getResourceAsStream("/chatbot/evaluation-scenarios.json"));
        for (var scenario : scenarios) {
            String token = UUID.randomUUID().toString(), id = null;
            Map<String,Object> result = new LinkedHashMap<>(); result.put("scenario",scenario.path("id").asText());
            result.put("model",model); List<Object> turns = new ArrayList<>();result.put("turns",turns);
            if (charged.get() >= budget) {
                result.put("status","SKIPPED_BUDGET");report.add(result);continue;
            }
            boolean correct = true;
            for (var turn : scenario.path("turns")) {
                if (charged.get() >= budget) { correct=false;result.put("status","SKIPPED_BUDGET");break; }
                var body=mapper.createObjectNode().put("message",turn.path("input").asText()).put("model",model);
                if(id!=null)body.put("conversationId",id);
                var response=mvc.perform(post("/api/chatbot").servletPath("/api/chatbot")
                        .cookie(new Cookie("dlm_guest",token)).contentType("application/json").content(body.toString()))
                        .andReturn().getResponse();
                var json=mapper.readTree(response.getContentAsString(StandardCharsets.UTF_8));
                Map<String,Object> actual=new LinkedHashMap<>();actual.put("input",turn.path("input"));
                actual.put("expectedStructuredAction",turn.path("action"));actual.put("expectedState",turn.path("expect"));
                actual.put("actualResponse",json);actual.put("httpStatus",response.getStatus());turns.add(actual);
                if(response.getStatus()!=200){correct=false;actual.put("failureReason","HTTP failure");break;}
                id=json.path("data").path("conversationId").asText();
                var saved=history.findByConversationIdOrderByIdDesc(id,PageRequest.of(0,1)).get(0);
                var snapshot=memory.load(id,new ConversationPrincipal(null,token));
                actual.put("actualStructuredAction",Map.of("intent",saved.getIntent(),"topicAction",saved.getContextAction()));
                actual.put("actualState",snapshot.activeTopic().context());
                String expected=turn.path("expect").path("intent").asText(turn.path("action").path("intent").asText());
                if(!expected.equals(saved.getIntent()))correct=false;
                var constraints=turn.path("expect");var data=json.path("data");
                if(constraints.has("products")&&constraints.path("products").asInt()!=data.path("products").size())correct=false;
                if(constraints.has("clarification")&&constraints.path("clarification").asBoolean()!=data.path("requiresClarification").asBoolean())correct=false;
            }
            // Full topic/reference/filter constraints and qualitative claims still require review of exported states.
            result.putIfAbsent("status",correct?"DETERMINISTIC_CHECKS_PASSED_REVIEW_REQUIRED":"FAIL");report.add(result);
        }
        Path path=Path.of("target","chatbot-evaluation","live-results.json");Files.createDirectories(path.getParent());
        mapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(),Map.of("chargedUpperBound",charged.get(),"budget",budget,"results",report));
    }
    private static void charge(AtomicInteger used,int budget,int amount) {
        while (true) {
            int before = used.get();
            if (before + amount > budget) throw new AiProviderException("Live evaluation budget exhausted",false,null);
            if (used.compareAndSet(before,before+amount)) return;
        }
    }
}
