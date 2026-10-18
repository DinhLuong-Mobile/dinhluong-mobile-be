package com.dinhluong.dlmstore.chatbot;

import com.dinhluong.dlmstore.chatbot.ai.AiRouter;
import com.dinhluong.dlmstore.chatbot.repository.*;
import com.dinhluong.dlmstore.chatbot.enums.ClarificationStatus;
import com.dinhluong.dlmstore.config.ProductDataStartupRunner;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.*;
import java.util.*;
import java.util.function.Function;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties={"chatbot.cookie.secure=false"})
@org.springframework.test.context.ActiveProfiles("test")
@org.springframework.context.annotation.Import(ChatbotFixtures.class)
class ClarificationHttpTest extends IsolatedChatbotDatabase {
    @Autowired TestRestTemplate http;
    @Autowired ObjectMapper mapper;
    @Autowired ChatbotClarificationRepository clarifications;
    @Autowired ChatbotTopicRepository topics;
    @Autowired ChatbotInteractionRepository history;
    @MockBean AiRouter ai;
    @MockBean ProductDataStartupRunner productRunner;
    @MockBean(name="initUsers") org.springframework.boot.CommandLineRunner initUsers;

    @Test void realHttpCompareChoiceAndRecallKeepTopicAndPersistOneResolution() throws Exception {
        action("{\"intent\":\"COMPARE\",\"contextAction\":\"NEW_TOPIC\",\"compareProductNames\":[\"Fixture Phone\",\"Fixture Phone 5\"]}");
        var headers=new HttpHeaders();headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HttpHeaders.COOKIE,"dlm_guest=http-clarification-fixture");
        var first=http.postForEntity("/api/chatbot",new HttpEntity<>(Map.of("message","Compare fixture phones"),headers),String.class);
        assertEquals(HttpStatus.OK,first.getStatusCode());
        var question=mapper.readTree(first.getBody()).path("data");String id=question.path("conversationId").asText();
        assertTrue(question.path("requiresClarification").asBoolean());
        long pendingId=question.path("clarificationId").asLong();
        doThrow(new com.dinhluong.dlmstore.chatbot.exception.AiProviderException("offline",true,null))
                .when(ai).generateAndValidate(any(),anyString(),any());
        var second=http.postForEntity("/api/chatbot",new HttpEntity<>(Map.of("message","Fixture Phone 1 nhé","conversationId",id),headers),String.class);
        assertEquals(HttpStatus.OK,second.getStatusCode());
        var answer=mapper.readTree(second.getBody()).path("data");
        assertEquals(question.path("topicId"),answer.path("topicId"));assertFalse(answer.path("requiresClarification").asBoolean());
        assertEquals(2,answer.path("products").size());assertFalse(answer.path("answer").asText().isBlank());
        var resolved=clarifications.findById(pendingId).orElseThrow();
        assertEquals(ClarificationStatus.RESOLVED,resolved.getStatus());assertEquals("COMPARE",resolved.getResolvedValue().get("originalIntent"));
        action("{\"intent\":\"RECALL\",\"referenceType\":\"LAST_COMPARED_PRODUCTS\"}");
        var third=http.postForEntity("/api/chatbot",new HttpEntity<>(Map.of("message","Recall compared phones","conversationId",id),headers),String.class);
        assertEquals(HttpStatus.OK,third.getStatusCode());
        var recalled=mapper.readTree(third.getBody()).path("data");
        assertEquals(answer.path("topicId"),recalled.path("topicId"));assertEquals(answer.path("products"),recalled.path("products"));
        assertEquals(1,topics.findByConversationIdOrderByUpdatedAtDesc(id).size());
        assertEquals(3,history.findByConversationIdOrderByIdDesc(id,org.springframework.data.domain.PageRequest.of(0,10)).size());
    }
    void action(String json) {
        doAnswer(call -> ((Function<String,Object>)call.getArgument(2)).apply(json)).when(ai).generateAndValidate(any(),anyString(),any());
    }
    @Test void concurrentStructuredAnswersResolveOnceAndRejectStaleSelection() throws Exception {
        action("{\"intent\":\"DETAIL\",\"contextAction\":\"NEW_TOPIC\",\"targetProductName\":\"Fixture Phone\"}");
        var headers=new HttpHeaders();headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HttpHeaders.COOKIE,"dlm_guest=http-concurrent-clarification-fixture");
        var first=http.postForEntity("/api/chatbot",new HttpEntity<>(Map.of("message","detail"),headers),String.class);
        assertEquals(HttpStatus.OK,first.getStatusCode());
        var data=mapper.readTree(first.getBody()).path("data");String id=data.path("conversationId").asText();
        long pendingId=data.path("clarificationId").asLong();
        var pending=clarifications.findById(pendingId).orElseThrow();
        long selected=((Number)((List<?>)pending.getCandidateData().get("candidateIds")).get(0)).longValue();
        var request=new HttpEntity<>(Map.of("message","chosen","conversationId",id,"clarificationId",pendingId,"selectedProductId",selected),headers);
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var gate=new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.Callable<ResponseEntity<String>> call=()->{gate.await();return http.postForEntity("/api/chatbot",request,String.class);};
            var a=pool.submit(call);var b=pool.submit(call);gate.countDown();
            var results=List.of(a.get(20,java.util.concurrent.TimeUnit.SECONDS),b.get(20,java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(Set.of(200,400),results.stream().map(r -> r.getStatusCode().value()).collect(java.util.stream.Collectors.toSet()));
            var success=mapper.readTree(results.stream().filter(r->r.getStatusCode().is2xxSuccessful()).findFirst().orElseThrow().getBody()).path("data");
            assertEquals(data.path("topicId"),success.path("topicId"));assertEquals(selected,success.path("products").get(0).path("id").asLong());
            assertEquals(ClarificationStatus.RESOLVED,clarifications.findById(pendingId).orElseThrow().getStatus());
            assertEquals(1,topics.findByConversationIdOrderByUpdatedAtDesc(id).size());
            assertEquals(2,history.findByConversationIdOrderByIdDesc(id,org.springframework.data.domain.PageRequest.of(0,10)).size());
        } finally { pool.shutdownNow(); }
    }
}
