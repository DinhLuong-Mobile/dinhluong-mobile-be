package com.dinhluong.dlmstore.chatbot;

import com.dinhluong.dlmstore.chatbot.ai.AiRouter;
import com.dinhluong.dlmstore.chatbot.repository.*;
import com.dinhluong.dlmstore.chatbot.service.*;
import com.dinhluong.dlmstore.chatbot.dto.*;
import com.dinhluong.dlmstore.chatbot.enums.*;
import com.dinhluong.dlmstore.chatbot.exception.AiProviderException;
import com.dinhluong.dlmstore.config.ProductDataStartupRunner;
import com.dinhluong.dlmstore.dto.GeminiFilterCriteria;
import com.dinhluong.dlmstore.security.CustomUserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.function.Function;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

@SpringBootTest(properties={"chatbot.cookie.secure=false"})
@org.springframework.test.context.ActiveProfiles("test")
@org.springframework.context.annotation.Import(ChatbotFixtures.class)
@AutoConfigureMockMvc(print=org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint.NONE)
@Transactional
class ChatbotIntegrationTest extends IsolatedChatbotDatabase {
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired ChatbotConversationRepository conversations;
    @Autowired ChatbotTopicRepository topics;
    @Autowired ChatbotInteractionRepository history;
    @Autowired ConversationSessionService sessions;
    @Autowired ConversationContextService contexts;
    @Autowired ClarificationService clarifications;
    @MockBean AiRouter ai;
    @MockBean ProductDataStartupRunner productRunner;
    @MockBean(name="initUsers") org.springframework.boot.CommandLineRunner initUsers;
    @BeforeEach void mockAi() {
        when(ai.generateAndValidate(any(),anyString(),any())).thenAnswer(invocation -> {
            Function<String,Object> validator = invocation.getArgument(2);
            return validator.apply("{\"intent\":\"CHAT\"}");
        });
        when(ai.generate(any(),anyString())).thenReturn("Hello");
    }
    String body(String message, String id) throws Exception {
        var body = new HashMap<String,Object>();body.put("message",message);
        if (id != null) body.put("conversationId",id);
        return mapper.writeValueAsString(body);
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.MethodSource("budgetResumeCases")
    void budgetAnswerResumesOriginalTopicWithoutAi(String originalIntent, String answer) throws Exception {
        if (answer.contains("15")) {
            productRepository.findAll().get(0).setDisplayPrice(java.math.BigDecimal.valueOf(17_000_000));
            productRepository.findAll().get(1).setDisplayPrice(java.math.BigDecimal.valueOf(18_000_000));
            productRepository.flush();
        }
        var cookie = new Cookie("dlm_guest","budget-resume-fixture");
        intent("{\"intent\":\""+originalIntent+"\",\"contextAction\":\"NEW_TOPIC\",\"rankingCriterion\":\"PRICE_ASC\",\"requiresClarification\":true,\"clarificationQuestion\":\"Ngân sách bao nhiêu?\"}");
        var first = mapper.readTree(send(null,cookie)).path("data");
        String id = first.path("conversationId").asText(); long topicId = first.path("topicId").asLong();
        long pendingId = first.path("clarificationId").asLong();
        doThrow(new AiProviderException("offline",true,null)).when(ai).generateAndValidate(any(),anyString(),any());
        var next = mvc.perform(post("/api/chatbot").cookie(cookie).contentType("application/json").content(body(answer,id)))
                .andExpect(status().isOk()).andReturn();
        var data = mapper.readTree(next.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).path("data");
        assertEquals(topicId,data.path("topicId").asLong()); assertFalse(data.path("requiresClarification").asBoolean());
        assertFalse(data.path("products").isEmpty());
        assertEquals(1,topics.findByConversationIdOrderByUpdatedAtDesc(id).size());
        var pending = clarificationRepository.findById(pendingId).orElseThrow();
        assertEquals(ClarificationStatus.RESOLVED,pending.getStatus());
        assertEquals(originalIntent,pending.getResolvedValue().get("originalIntent"));
        var rows=history.findByConversationIdOrderByIdDesc(id,org.springframework.data.domain.PageRequest.of(0,10));
        assertEquals(2,rows.size()); assertEquals(originalIntent,rows.get(0).getIntent());
        assertFalse(data.path("answer").asText().isBlank());
        assertEquals(20_000_000,((Number)contexts.getContext(id,new ConversationPrincipal(null,"budget-resume-fixture"),topicId).get("maxPrice")).intValue());
    }
    static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> budgetResumeCases() {
        return java.util.stream.Stream.of("RECOMMEND","SEARCH","RANK").flatMap(action ->
                java.util.stream.Stream.of("20 triệu","Tôi có 20 triệu","Dưới 20 củ","15–20 triệu")
                        .map(answer -> org.junit.jupiter.params.provider.Arguments.of(action,answer)));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"invalid","unrelated","cancel","new","expired","outside-id","stale-id"})
    void pendingLifecycleGuards(String mode) throws Exception {
        var cookie=new Cookie("dlm_guest","pending-guards-fixture");
        intent("{\"intent\":\"DETAIL\",\"contextAction\":\"NEW_TOPIC\",\"targetProductName\":\"Fixture Phone\"}");
        var first=mapper.readTree(send(null,cookie)).path("data");String id=first.path("conversationId").asText();
        long pendingId=first.path("clarificationId").asLong();long topicId=first.path("topicId").asLong();
        intent("{\"intent\":\"SEARCH\",\"contextAction\":\"NEW_TOPIC\"}");
        String text=mode.equals("cancel")?"hủy":"unrelated question";
        String payload=body(text,id);
        if(mode.equals("new")) intent("{\"intent\":\"SEARCH\",\"messageAction\":\"NEW_REQUEST\",\"contextAction\":\"NEW_TOPIC\"}");
        if(mode.equals("expired")) {
            clarificationRepository.findById(pendingId).orElseThrow().setExpiresAt(java.time.LocalDateTime.now().minusSeconds(1));
            clarificationRepository.flush();
        }
        if(mode.endsWith("-id") || mode.equals("expired")) payload=mapper.writeValueAsString(Map.of("message","choose","conversationId",id,
                "selectedProductId",mode.equals("outside-id")?Long.MAX_VALUE:productRepository.findAll().get(0).getId(),
                "clarificationId",mode.equals("stale-id")?Long.MAX_VALUE:pendingId));
        var result=mvc.perform(post("/api/chatbot").cookie(cookie).contentType("application/json").content(payload))
                .andExpect(status().is(mode.endsWith("-id") || mode.equals("expired")?400:200)).andReturn();
        var stored=clarificationRepository.findById(pendingId).orElseThrow();
        if(mode.equals("cancel")||mode.equals("new")) assertEquals(ClarificationStatus.CANCELLED,stored.getStatus());
        else if(mode.equals("expired")) assertEquals(ClarificationStatus.EXPIRED,stored.getStatus());
        else assertEquals(ClarificationStatus.PENDING,stored.getStatus());
        assertEquals(mode.equals("new")?2:1,topics.findByConversationIdOrderByUpdatedAtDesc(id).size());
        if(!mode.endsWith("-id") && !mode.equals("expired")) {
            var data=mapper.readTree(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).path("data");
            if(!mode.equals("new")) assertEquals(topicId,data.path("topicId").asLong());
            if(mode.equals("invalid")||mode.equals("unrelated")) {
                assertEquals(pendingId,data.path("clarificationId").asLong());assertTrue(data.path("requiresClarification").asBoolean());
            }
        }
    }

    @Autowired ChatbotClarificationRepository clarificationRepository;
    @Test void identicalNamesRequireDiscriminableChoiceAndStructuredIdResumesDetail() throws Exception {
        var products=productRepository.findAll().stream().sorted(java.util.Comparator.comparing(com.dinhluong.dlmstore.entity.Product::getId)).toList();
        products.get(0).setName("Fixture Twin");products.get(1).setName("Fixture Twin");productRepository.flush();
        var cookie=new Cookie("dlm_guest","identical-fixture");
        intent("{\"intent\":\"DETAIL\",\"targetProductName\":\"Fixture Twin\",\"contextAction\":\"NEW_TOPIC\"}");
        var first=mapper.readTree(send(null,cookie)).path("data");String id=first.path("conversationId").asText();
        assertTrue(first.path("answer").asText().contains("ID "+products.get(0).getId()));
        assertTrue(first.path("answer").asText().contains("ID "+products.get(1).getId()));
        var ambiguous=mvc.perform(post("/api/chatbot").cookie(cookie).contentType("application/json").content(body("Fixture Twin",id)))
                .andExpect(status().isOk()).andReturn();
        var repeated=mapper.readTree(ambiguous.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).path("data");
        assertEquals(first.path("clarificationId"),repeated.path("clarificationId"));assertEquals(first.path("topicId"),repeated.path("topicId"));
        String payload=mapper.writeValueAsString(Map.of("message","this one","conversationId",id,"clarificationId",first.path("clarificationId").asLong(),"selectedProductId",products.get(1).getId()));
        var result=mvc.perform(post("/api/chatbot").cookie(cookie).contentType("application/json").content(payload)).andExpect(status().isOk()).andReturn();
        var data=mapper.readTree(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).path("data");
        assertFalse(data.path("requiresClarification").asBoolean());assertEquals(products.get(1).getId(),data.path("products").get(0).path("id").asLong());
        assertEquals(ClarificationStatus.RESOLVED,clarificationRepository.findById(first.path("clarificationId").asLong()).orElseThrow().getStatus());
        assertEquals(1,topics.findByConversationIdOrderByUpdatedAtDesc(id).size());
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"Fixture Model 128GB nhé","Fixture Model 128GB (Copy)","So sánh Fixture Model 128GB với Fixture Counterpart","Fixtur Model 128GB","thứ 2","structured","legacy"})
    void namedChoiceResumesCompareAndPreservesCounterpart(String answer) throws Exception {
        var products = productRepository.findAll().stream().sorted(java.util.Comparator.comparing(com.dinhluong.dlmstore.entity.Product::getId)).toList();
        products.get(0).setName("Fixture Model 128GB"); products.get(1).setName("Fixture Model 128GB (Copy)");
        products.get(2).setName("Fixture Counterpart"); productRepository.flush();
        var cookie = new Cookie("dlm_guest","named-resume-fixture");
        intent("{\"intent\":\"COMPARE\",\"contextAction\":\"NEW_TOPIC\",\"compareProductNames\":[\"Fixture Model\",\"Fixture Counterpart\"]}");
        var first = mapper.readTree(send(null,cookie)).path("data"); String id=first.path("conversationId").asText();
        assertTrue(first.path("requiresClarification").asBoolean());
        var pending = clarificationRepository.findById(first.path("clarificationId").asLong()).orElseThrow();
        assertEquals(2,((List<?>)pending.getCandidateData().get("candidateIds")).size());
        if (answer.equals("legacy")) {
            pending.setCandidateData(Map.of("criteria",pending.getCandidateData().get("criteria"),
                    "candidateIds",List.of(products.get(2).getId())));
            clarificationRepository.flush();answer="Fixture Model 128GB nhé";
        }
        doThrow(new AiProviderException("offline",true,null)).when(ai).generateAndValidate(any(),anyString(),any());
        String payload = body(answer,id);
        if (answer.equals("structured")) payload = mapper.writeValueAsString(Map.of("message","Choose this",
                "conversationId",id,"selectedProductId",products.get(0).getId(),"clarificationId",pending.getId()));
        var result = mvc.perform(post("/api/chatbot").cookie(cookie).contentType("application/json").content(payload))
                .andExpect(status().isOk()).andReturn();
        var data=mapper.readTree(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).path("data");
        assertEquals(first.path("topicId"),data.path("topicId")); assertFalse(data.path("requiresClarification").asBoolean());
        var ids=new HashSet<Long>();data.path("products").forEach(p -> ids.add(p.path("id").asLong()));
        assertEquals(Set.of(products.get(answer.contains("Copy") || answer.equals("thứ 2")?1:0).getId(),products.get(2).getId()),ids);
        assertEquals(ClarificationStatus.RESOLVED,clarificationRepository.findById(pending.getId()).orElseThrow().getStatus());
        assertEquals(1,topics.findByConversationIdOrderByUpdatedAtDesc(id).size());
    }
    @Test void guestRoundTripPersistsTopicHistoryAndReusesCookie() throws Exception {
        var result = mvc.perform(post("/api/chatbot").contentType("application/json").content(body("hi",null)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.topicId").isNumber()).andReturn();
        var cookie = result.getResponse().getCookie("dlm_guest");assertNotNull(cookie);assertTrue(cookie.isHttpOnly());
        String id = mapper.readTree(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).path("data").path("conversationId").asText();
        assertTrue(conversations.findById(id).isPresent());
        assertEquals(1, topics.findByConversationIdOrderByUpdatedAtDesc(id).size());
        mvc.perform(post("/api/chatbot").cookie(cookie).contentType("application/json").content(body("again",id)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.conversationId").value(id));
        assertEquals(2,history.findByConversationIdOrderByCreatedAtDescIdDesc(id,org.springframework.data.domain.PageRequest.of(0,10)).size());
    }
    @Test void wrongOrMissingCookieCannotReadExistingConversation() throws Exception {
        var c=sessions.createConversation(new ConversationPrincipal(null,"owner"));
        mvc.perform(post("/api/chatbot").cookie(new Cookie("dlm_guest","wrong")).contentType("application/json").content(body("hi",c.getId())))
                .andExpect(status().isForbidden());
    }
    @Test void missingCookieCannotReadExistingConversation() throws Exception {
        var c=sessions.createConversation(new ConversationPrincipal(null,"owner"));
        mvc.perform(post("/api/chatbot").contentType("application/json").content(body("hi",c.getId())))
                .andExpect(status().isForbidden());
    }
    @Test void authenticatedPrincipalCreatesUserConversation() throws Exception {
        // Use a real existing user to respect the database foreign key; no user seed writes.
        var user = userRepository.findAll().stream().findFirst().orElseThrow();
        var principal=new CustomUserPrincipal(user.getId(),user.getEmail(),"",List.of());
        var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(principal,null,List.of());
        var r=mvc.perform(post("/api/chatbot").with(authentication(auth)).contentType("application/json").content(body("hi",null)))
                .andExpect(status().isOk()).andReturn();
        String id=mapper.readTree(r.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).path("data").path("conversationId").asText();
        assertEquals(user.getId(),conversations.findById(id).orElseThrow().getUserId());
        assertNull(r.getResponse().getCookie("dlm_guest"));
    }
    @Autowired com.dinhluong.dlmstore.repository.UserRepository userRepository;
    @Test void expiredAndClosedConversationsReturnConflict() throws Exception {
        var c=sessions.createConversation(new ConversationPrincipal(null,"owner"));c.setStatus(ConversationStatus.CLOSED);
        mvc.perform(post("/api/chatbot").cookie(new Cookie("dlm_guest","owner")).contentType("application/json").content(body("hi",c.getId())))
                .andExpect(status().isConflict());
    }
    @Test void providerFailureIs503() throws Exception {
        doThrow(new AiProviderException("offline",true,null)).when(ai).generateAndValidate(any(),anyString(),any());
        mvc.perform(post("/api/chatbot").contentType("application/json").content(body("hi",null)))
                .andExpect(status().isServiceUnavailable());
    }
    @Test void blankMalformedAndInvalidJwtAreRejected() throws Exception {
        mvc.perform(post("/api/chatbot").contentType("application/json").content(body(" ",null))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/chatbot").contentType("application/json").content("{" )).andExpect(status().isBadRequest());
        mvc.perform(post("/api/chatbot").servletPath("/api/chatbot").header("Authorization","Bearer invalid").contentType("application/json").content(body("hi",null)))
                .andExpect(status().isUnauthorized());
    }
    @Test void untrustedOriginIsRejected() throws Exception {
        mvc.perform(post("/api/chatbot").header("Origin","https://untrusted.example").contentType("application/json").content(body("hi",null)))
                .andExpect(status().isForbidden());
    }

    void intent(String json) {
        doAnswer(invocation -> ((Function<String,Object>)invocation.getArgument(2)).apply(json))
                .when(ai).generateAndValidate(any(),anyString(),any());
    }
    String send(String id, Cookie cookie) throws Exception {
        var request=post("/api/chatbot").contentType("application/json").content(body("request",id));
        if(cookie!=null)request.cookie(cookie);
        return mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
    }
    @Test void contextPersistsMergesAndClearsAcrossTurns() throws Exception {
        intent("{\"intent\":\"SEARCH\",\"brands\":[\"Samsung\"],\"maxPrice\":10000000}");
        var cookie=new Cookie("dlm_guest","owner");
        var first=mapper.readTree(send(null,cookie)).path("data");
        String id=first.path("conversationId").asText();Long topic=first.path("topicId").asLong();
        intent("{\"intent\":\"SEARCH\",\"ram\":\"8GB\"}");send(id,cookie);
        var context=contexts.getContext(id,new ConversationPrincipal(null,"owner"),topic);
        assertEquals(List.of("Samsung"),context.get("brands"));assertEquals("8GB",context.get("ram"));
        intent("{\"intent\":\"SEARCH\",\"clearFields\":[\"brands\"],\"maxPrice\":15000000}");send(id,cookie);
        context=contexts.getContext(id,new ConversationPrincipal(null,"owner"),topic);
        assertFalse(context.containsKey("brands"));assertEquals(15000000,((Number)context.get("maxPrice")).intValue());
    }
    @Test void clarificationCreatesResolvesAndTopicCanSwitchBack() throws Exception {
        intent("{\"intent\":\"CHAT\",\"requiresClarification\":true,\"clarificationQuestion\":\"Budget?\"}");
        var cookie=new Cookie("dlm_guest","owner");var principal=new ConversationPrincipal(null,"owner");
        var first=mapper.readTree(send(null,cookie)).path("data");String id=first.path("conversationId").asText();
        Long topic=first.path("topicId").asLong();assertTrue(first.path("requiresClarification").asBoolean());
        assertTrue(clarifications.getPending(id,principal,topic).isPresent());
        intent("{\"intent\":\"CHAT\",\"messageAction\":\"ANSWER_CLARIFICATION\",\"maxPrice\":10000000}");send(id,cookie);
        assertTrue(clarifications.getPending(id,principal,topic).isEmpty());
        intent("{\"intent\":\"SEARCH\",\"contextAction\":\"NEW_TOPIC\",\"topicName\":\"Gaming\"}");
        var next=mapper.readTree(send(id,cookie)).path("data");assertNotEquals(topic,next.path("topicId").asLong());
        intent("{\"intent\":\"SEARCH\",\"contextAction\":\"SWITCH_TOPIC\",\"targetTopicId\":"+topic+"}");
        assertEquals(topic,mapper.readTree(send(id,cookie)).path("data").path("topicId").asLong());
    }
    @Test void expiredConversationIsRejected() throws Exception {
        var c=sessions.createConversation(new ConversationPrincipal(null,"owner"));
        c.setExpiresAt(java.time.LocalDateTime.now().minusDays(1));
        mvc.perform(post("/api/chatbot").cookie(new Cookie("dlm_guest","owner")).contentType("application/json").content(body("hi",c.getId())))
                .andExpect(status().isConflict());
    }

    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
    @Test
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void concurrentRequestsShareOneTopicAndProviderFailureRollsBack() throws Exception {
        var tx=new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        String token=UUID.randomUUID().toString();var principal=new ConversationPrincipal(null,token);
        String id=tx.execute(status->sessions.createConversation(principal).getId());
        var executor=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var gate=new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.Callable<String> task=()->{gate.await();return send(id,new Cookie("dlm_guest",token));};
            var first=executor.submit(task);var second=executor.submit(task);gate.countDown();
            first.get(20,java.util.concurrent.TimeUnit.SECONDS);second.get(20,java.util.concurrent.TimeUnit.SECONDS);
            assertEquals(1,topics.findByConversationIdOrderByUpdatedAtDesc(id).size());
            assertEquals(2,history.findByConversationIdOrderByCreatedAtDescIdDesc(id,org.springframework.data.domain.PageRequest.of(0,10)).size());
            var before=conversations.findById(id).orElseThrow().getLastMessageAt();
            doThrow(new AiProviderException("offline",true,null)).when(ai).generateAndValidate(any(),anyString(),any());
            mvc.perform(post("/api/chatbot").cookie(new Cookie("dlm_guest",token)).contentType("application/json").content(body("fail",id)))
                    .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.conversationId").value(id));
            assertEquals(before,conversations.findById(id).orElseThrow().getLastMessageAt());
            assertEquals(2,history.findByConversationIdOrderByCreatedAtDescIdDesc(id,org.springframework.data.domain.PageRequest.of(0,10)).size());
        } finally {
            executor.shutdownNow();
            tx.executeWithoutResult(status->{
                // Delete only rows belonging to this test's randomly generated conversation.
                jdbc.update("delete from chatbot_interactions where conversation_id=?",id);
                jdbc.update("delete from chatbot_clarifications where conversation_id=?",id);
                jdbc.update("update chatbot_conversations set active_topic_id=null where id=?",id);
                jdbc.update("delete from chatbot_topics where conversation_id=?",id);
                jdbc.update("delete from chatbot_conversations where id=?",id);
            });
        }
    }

    @Autowired com.dinhluong.dlmstore.utils.JwtTokenProvider jwt;
    @Test void validJwtReachesTheControllerAsAuthenticatedUser() throws Exception {
        var user=userRepository.findAll().stream().filter(u->!Boolean.FALSE.equals(u.getIsEnabled())).findFirst().orElseThrow();
        String token=jwt.generateToken(user);
        var r=mvc.perform(post("/api/chatbot").servletPath("/api/chatbot").header("Authorization","Bearer "+token)
                .contentType("application/json").content(body("hi",null))).andExpect(status().isOk()).andReturn();
        String id=mapper.readTree(r.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).path("data").path("conversationId").asText();
        assertEquals(user.getId(),conversations.findById(id).orElseThrow().getUserId());
        assertNull(r.getResponse().getCookie("dlm_guest"));
    }
    @Test void guestCannotAccessAuthenticatedConversation() throws Exception {
        var user=userRepository.findAll().stream().findFirst().orElseThrow();
        var c=sessions.createConversation(new ConversationPrincipal(user.getId(),null));
        mvc.perform(post("/api/chatbot").cookie(new Cookie("dlm_guest","guest")).contentType("application/json").content(body("hi",c.getId())))
                .andExpect(status().isForbidden());
    }
    @Test void userCannotAccessAnotherUsersConversation() throws Exception {
        var user=userRepository.findAll().stream().findFirst().orElseThrow();
        var c=sessions.createConversation(new ConversationPrincipal(user.getId(),null));
        var other=new CustomUserPrincipal(user.getId()+100000L,"other@example.test","",List.of());
        var auth=new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(other,null,List.of());
        mvc.perform(post("/api/chatbot").with(authentication(auth)).contentType("application/json").content(body("hi",c.getId())))
                .andExpect(status().isForbidden());
    }

    @Autowired ChatbotProductSearchService productSearch;
    @Autowired ChatbotCatalogService catalog;
    @Autowired ConversationMemoryService memories;
    @Autowired ChatbotBusinessService business;
    @Autowired ConversationTurnStore turnStore;

    @Test void R05_R17_R19_positionSurvivesSmallTalkAndReloadsCurrentPrice() throws Exception {
        var cookie = new Cookie("dlm_guest", "position-fixture");
        structuredTurn("{\"intent\":\"SEARCH\"}");
        var first = mapper.readTree(send(null, cookie)).path("data");
        String id = first.path("conversationId").asText();
        long secondId = first.path("products").get(1).path("id").asLong();
        var product = productRepository.findById(secondId).orElseThrow();
        product.setDisplayPrice(new java.math.BigDecimal("4321000")); productRepository.flush();
        structuredTurn("{\"intent\":\"CHAT\",\"contextAction\":\"NEW_TOPIC\",\"topicName\":\"Greeting\"}");
        var smallTalk = mapper.readTree(send(id, cookie)).path("data");
        assertEquals(first.path("topicId"), smallTalk.path("topicId"));
        structuredTurn("{\"intent\":\"DETAIL\",\"referenceType\":\"PRODUCT_BY_POSITION\",\"productPositions\":[2]}");
        var second = mapper.readTree(send(id, cookie)).path("data");
        assertEquals(secondId, second.path("products").get(0).path("id").asLong());
        assertEquals(4321000, second.path("products").get(0).path("price").asInt());
        assertTrue(second.path("answer").asText().contains("4321000"));
    }

    @Test void R06_rankPriceUsesComparedPairOnly() throws Exception {
        var cookie = new Cookie("dlm_guest", "price-fixture");
        structuredTurn("{\"intent\":\"COMPARE\",\"compareProductNames\":[\"Fixture Phone 2\",\"Fixture Phone 4\"]}");
        var first = mapper.readTree(send(null, cookie)).path("data");
        structuredTurn("{\"intent\":\"RANK\",\"referenceType\":\"LAST_COMPARED_PRODUCTS\",\"rankingCriterion\":\"PRICE_ASC\",\"resultLimit\":2}");
        var second = mapper.readTree(send(first.path("conversationId").asText(), cookie)).path("data");
        assertEquals(first.path("products").get(0).path("id"), second.path("products").get(0).path("id"));
        assertEquals(2, second.path("products").size());
        assertTrue(second.path("products").get(0).path("price").asDouble() <= second.path("products").get(1).path("price").asDouble());
    }

    @Test void R07_R08_R18_resumeTopicAndRecallBothTopics() throws Exception {
        var cookie = new Cookie("dlm_guest", "topics-fixture");
        structuredTurn("{\"intent\":\"SEARCH\",\"brands\":[\"Samsung\"],\"maxPrice\":10000000,\"topicName\":\"Samsung budget\"}");
        var first = mapper.readTree(send(null, cookie)).path("data"); String id = first.path("conversationId").asText();
        structuredTurn("{\"intent\":\"SEARCH\",\"contextAction\":\"NEW_TOPIC\",\"topicName\":\"Photography\",\"usageGoal\":\"photography\",\"maxPrice\":20000000}");
        var other = mapper.readTree(send(id, cookie)).path("data"); assertNotEquals(first.path("topicId"), other.path("topicId"));
        structuredTurn("{\"intent\":\"RECALL\",\"referenceType\":\"CONVERSATION_HISTORY\"}");
        var historyAnswer = mapper.readTree(send(id, cookie)).path("data").path("answer").asText();
        assertTrue(historyAnswer.contains("Samsung budget")); assertTrue(historyAnswer.contains("Photography"));
        structuredTurn("{\"intent\":\"RECALL\",\"contextAction\":\"SWITCH_TOPIC\",\"topicDescription\":\"Samsung budget\",\"referenceType\":\"LAST_PRESENTED_LIST\"}");
        var resumed = mapper.readTree(send(id, cookie)).path("data");
        assertEquals(first.path("topicId"), resumed.path("topicId"));
        assertEquals(first.path("products"), resumed.path("products"));
        var context = contexts.getContext(id, new ConversationPrincipal(null,"topics-fixture"), first.path("topicId").asLong());
        assertEquals(10000000, ((Number)context.get("maxPrice")).intValue());
    }

    @Test void R09_R10_clarificationRetainsOriginalCompareActionAndCandidateOrder() throws Exception {
        var cookie = new Cookie("dlm_guest", "clarify-fixture");
        structuredTurn("{\"intent\":\"SEARCH\"}"); var first = mapper.readTree(send(null, cookie)).path("data");
        String id = first.path("conversationId").asText();
        structuredTurn("{\"intent\":\"COMPARE\",\"referenceType\":\"LAST_PRESENTED_LIST\"}");
        var question = mapper.readTree(send(id, cookie)).path("data"); assertTrue(question.path("requiresClarification").asBoolean());
        structuredTurn("{\"intent\":\"CHAT\",\"messageAction\":\"ANSWER_CLARIFICATION\",\"productPositions\":[1,3]}");
        var compared = mapper.readTree(send(id, cookie)).path("data");
        assertFalse(compared.path("requiresClarification").asBoolean()); assertEquals(2, compared.path("products").size());
        assertEquals(first.path("products").get(0).path("id"), compared.path("products").get(0).path("id"));
        assertEquals(first.path("products").get(2).path("id"), compared.path("products").get(1).path("id"));
        assertTrue(compared.path("answer").asText().contains("| Sản phẩm |"));
    }

    @Test void R11_emptySearchIsNotAHistoryReferenceFailure() throws Exception {
        structuredTurn("{\"intent\":\"SEARCH\",\"maxPrice\":1}");
        var answer = mapper.readTree(send(null, new Cookie("dlm_guest","empty-fixture"))).path("data");
        assertEquals(0, answer.path("products").size()); assertFalse(answer.path("requiresClarification").asBoolean());
        assertTrue(answer.path("answer").asText().contains("bộ lọc"));
    }

    @Test void R12_inactiveReferenceIsNotAdvertised() throws Exception {
        var cookie = new Cookie("dlm_guest", "inactive-fixture"); structuredTurn("{\"intent\":\"SEARCH\"}");
        var first = mapper.readTree(send(null,cookie)).path("data");
        var p = productRepository.findById(first.path("products").get(1).path("id").asLong()).orElseThrow();
        p.setStatus(com.dinhluong.dlmstore.entity.Enums.ProductStatus.INACTIVE); productRepository.flush();
        structuredTurn("{\"intent\":\"DETAIL\",\"referenceType\":\"PRODUCT_BY_POSITION\",\"productPositions\":[2]}");
        var answer = mapper.readTree(send(first.path("conversationId").asText(),cookie)).path("data");
        assertEquals(0,answer.path("products").size());assertTrue(answer.path("requiresClarification").asBoolean());
    }

    @Test void R20_twoVariantsHaveSeparateCurrentPricesAndSingleModelCard() throws Exception {
        var cookie = new Cookie("dlm_guest","variant-fixture");
        structuredTurn("{\"intent\":\"COMPARE\",\"comparisonVariants\":[{\"productName\":\"Fixture Phone 1\",\"rom\":\"256GB\",\"color\":\"Black\"},{\"productName\":\"Fixture Phone 1\",\"rom\":\"512GB\",\"color\":\"Black\"}]}");
        var compared = mapper.readTree(send(null,cookie)).path("data");
        assertFalse(compared.path("requiresClarification").asBoolean());assertEquals(1,compared.path("products").size());
        String table=compared.path("answer").asText();assertTrue(table.contains("256GB"));assertTrue(table.contains("512GB"));
        assertTrue(table.contains("5500000"));assertTrue(table.contains("6000000"));
    }

    @Test
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void networkInferenceHasNoOpenDatabaseTransaction() throws Exception {
        doAnswer(invocation -> {
            assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
            return ((Function<String,Object>)invocation.getArgument(2)).apply("{\"intent\":\"SEARCH\"}");
        }).when(ai).generateAndValidate(any(),anyString(),any());
        send(null,new Cookie("dlm_guest","no-network-lock-fixture"));
    }

    @Test void stalePlanCannotOverwriteAnotherCommittedTurn() throws Exception {
        var principal=new ConversationPrincipal(null,"stale-fixture");
        var snapshot=memories.load(null,principal);var c=new GeminiFilterCriteria();c.setIntent("SEARCH");
        c.setContextAction(ContextAction.CONTINUE);c.setMessageAction(MessageAction.NONE);
        var plan=business.prepare(snapshot,c,"search",AiModel.AUTO);
        turnStore.commit(principal,"first",AiModel.AUTO,plan);
        assertThrows(com.dinhluong.dlmstore.chatbot.exception.ConversationStateException.class,
                ()->turnStore.commit(principal,"stale",AiModel.AUTO,plan));
    }

    @Test void productPriceChangeBetweenPlanningAndCommitIsRejected() {
        var principal=new ConversationPrincipal(null,"product-race-fixture");var snapshot=memories.load(null,principal);
        var c=new GeminiFilterCriteria();c.setIntent("SEARCH");c.setContextAction(ContextAction.CONTINUE);c.setMessageAction(MessageAction.NONE);
        var plan=business.prepare(snapshot,c,"search",AiModel.AUTO);
        var p=productRepository.findById(plan.response().getProducts().get(0).getId()).orElseThrow();
        p.setDisplayPrice(p.getDisplayPrice().add(java.math.BigDecimal.ONE));productRepository.flush();
        assertThrows(com.dinhluong.dlmstore.chatbot.exception.ConversationStateException.class,
                ()->turnStore.commit(principal,"search",AiModel.AUTO,plan));
    }

    @Test void boundedMemoryAndPromptDoNotLeakAcrossConversations() throws Exception {
        var principal=new ConversationPrincipal(null,"large-memory-fixture");var session=sessions.createConversation(principal);
        var rows=new ArrayList<com.dinhluong.dlmstore.chatbot.entity.ChatbotInteraction>();
        for(int i=0;i<230;i++) rows.add(com.dinhluong.dlmstore.chatbot.entity.ChatbotInteraction.builder()
                .conversationId(session.getId()).query("x".repeat(1200)).response("y".repeat(1200)).intent("CHAT").build());
        history.saveAllAndFlush(rows);
        var snapshot=memories.load(session.getId(),principal);assertTrue(snapshot.recentTurns().size()<=12);
        assertTrue(snapshot.recentTurns().stream().mapToInt(t->t.query().length()+t.answer().length()).sum()<=6000);
        assertThrows(SecurityException.class,()->memories.load(session.getId(),new ConversationPrincipal(null,"other-guest")));
    }

    @Test void ambiguousNameAndMissingReferenceAskDifferentQuestionsFromEmptySearch() throws Exception {
        structuredTurn("{\"intent\":\"DETAIL\",\"targetProductName\":\"Fixture Phone\"}");
        var named=mapper.readTree(send(null,new Cookie("dlm_guest","name-fixture"))).path("data");
        assertTrue(named.path("requiresClarification").asBoolean());assertEquals(0,named.path("products").size());
        structuredTurn("{\"intent\":\"RECALL\",\"referenceType\":\"LAST_COMPARED_PRODUCTS\"}");
        var missing=mapper.readTree(send(null,new Cookie("dlm_guest","reference-fixture"))).path("data");
        assertTrue(missing.path("requiresClarification").asBoolean());
    }

    @Test void rawAiProductIdCannotGrantDatabaseAccess() throws Exception {
        structuredTurn("{\"intent\":\"DETAIL\",\"targetProductName\":\"Fixture Phone 1\",\"productIds\":[987654321],\"sql\":\"select * from users\"}");
        var answer=mapper.readTree(send(null,new Cookie("dlm_guest","injection-fixture"))).path("data");
        assertEquals(1,answer.path("products").size());assertNotEquals(987654321L,answer.path("products").get(0).path("id").asLong());
    }

    @Test void oversizedJsonAndInvalidRequestIdentifiersFailBeforeAi() throws Exception {
        mvc.perform(post("/api/chatbot").header("Origin","https://localhost:5173").contentType("application/json")
                .content(body("x".repeat(17000),null))).andExpect(status().isPayloadTooLarge())
                .andExpect(header().string("Access-Control-Allow-Origin","https://localhost:5173"));
        mvc.perform(post("/api/chatbot").contentType("application/json").content(body("x".repeat(4001),null)))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/chatbot").contentType("application/json").content(body("request","invalid-id")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(ai);
    }

    @Test void catalogFiltersMappedEavChipCameraAndVariantAvailability() {
        var c=new GeminiFilterCriteria();c.setIntent("SEARCH");c.setChip("Fixture Chip 3");c.setCamera("70MP");
        c.setColor("Black");c.setRam("8GB");c.setRom("256GB");c.setInStockOnly(true);
        var found=catalog.search(c);assertEquals(1,found.aiProducts().size());
        assertEquals("Fixture Phone 3",found.aiProducts().get(0).getName());
        assertEquals("Fixture Chip 3",found.aiProducts().get(0).getSpecs().get("chip"));
        assertEquals("70MP",found.aiProducts().get(0).getSpecs().get("camera"));
        c.setColor("Unknown color");assertTrue(catalog.search(c).aiProducts().isEmpty());
    }

    @Test void paginationUsesStableDatabasePagesWithoutSkippingDisplayedRows() throws Exception {
        var cookie=new Cookie("dlm_guest","pagination-fixture");
        structuredTurn("{\"intent\":\"SEARCH\",\"resultLimit\":2,\"resultPage\":0}");
        var first=mapper.readTree(send(null,cookie)).path("data");
        structuredTurn("{\"intent\":\"SEARCH\",\"resultLimit\":2,\"resultPage\":1}");
        var next=mapper.readTree(send(first.path("conversationId").asText(),cookie)).path("data");
        assertEquals(2,first.path("products").size());assertEquals(2,next.path("products").size());
        Set<Long> ids=new HashSet<>();first.path("products").forEach(p->ids.add(p.path("id").asLong()));
        next.path("products").forEach(p->assertFalse(ids.contains(p.path("id").asLong())));
    }

    @Test void comparisonSurvivesMoreThanTwoHundredSmallTalkTurns() throws Exception {
        var cookie=new Cookie("dlm_guest","archived-comparison-fixture");
        structuredTurn("{\"intent\":\"COMPARE\",\"compareProductNames\":[\"Fixture Phone 1\",\"Fixture Phone 2\"]}");
        var first=mapper.readTree(send(null,cookie)).path("data");String id=first.path("conversationId").asText();
        var rows=new ArrayList<com.dinhluong.dlmstore.chatbot.entity.ChatbotInteraction>();
        for(int i=0;i<210;i++)rows.add(com.dinhluong.dlmstore.chatbot.entity.ChatbotInteraction.builder()
                .conversationId(id).topicId(first.path("topicId").asLong()).intent("CHAT").query("fixture small talk").response("fixture reply").build());
        history.saveAllAndFlush(rows);
        for(int i=0;i<105;i++) {
            newTopicFixture(id,new ConversationPrincipal(null,"archived-comparison-fixture"),i);
        }
        structuredTurn("{\"intent\":\"RECALL\",\"referenceType\":\"LAST_COMPARED_PRODUCTS\"}");
        assertEquals(first.path("products"),mapper.readTree(send(id,cookie)).path("data").path("products"));
    }

    @Autowired ConversationTopicService topicService;
    void newTopicFixture(String id,ConversationPrincipal principal,int index) {
        topicService.createTopic(id,principal,"Later fixture topic "+index);
    }

    @Test void clarificationAttemptsTerminateAndDoNotRestartImplicitly() throws Exception {
        var cookie=new Cookie("dlm_guest","attempt-limit-fixture");
        structuredTurn("{\"intent\":\"COMPARE\",\"requiresClarification\":true,\"clarificationQuestion\":\"Which two?\"}");
        var first=mapper.readTree(send(null,cookie)).path("data");String id=first.path("conversationId").asText();
        send(id,cookie);var third=mapper.readTree(send(id,cookie)).path("data");assertFalse(third.path("requiresClarification").asBoolean());
        var fourth=mapper.readTree(send(id,cookie)).path("data");assertFalse(fourth.path("requiresClarification").asBoolean());
        assertTrue(clarifications.getPending(id,new ConversationPrincipal(null,"attempt-limit-fixture"),first.path("topicId").asLong()).isEmpty());
    }

    @Test
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void providerTimeoutReleasesTurnForWaitingRequest() throws Exception {
        String token="timeout-"+UUID.randomUUID();var c=sessions.createConversation(new ConversationPrincipal(null,token));
        var entered=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);
        var calls=new java.util.concurrent.atomic.AtomicInteger();
        doAnswer(invocation->{
            assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
            if(calls.getAndIncrement()==0){entered.countDown();release.await(5,java.util.concurrent.TimeUnit.SECONDS);
                throw new AiProviderException("fixture timeout",true,new java.net.SocketTimeoutException());}
            return ((Function<String,Object>)invocation.getArgument(2)).apply("{\"intent\":\"SEARCH\"}");
        }).when(ai).generateAndValidate(any(),anyString(),any());
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<Integer> task=()->mvc.perform(post("/api/chatbot")
                    .cookie(new Cookie("dlm_guest",token)).contentType("application/json").content(body("request",c.getId())))
                    .andReturn().getResponse().getStatus();
            var first=pool.submit(task);assertTrue(entered.await(5,java.util.concurrent.TimeUnit.SECONDS));
            var second=pool.submit(task);release.countDown();
            assertEquals(503,first.get(10,java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(200,second.get(10,java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(1,history.findByConversationIdOrderByIdDesc(c.getId(),org.springframework.data.domain.PageRequest.of(0,10)).size());
        } finally {release.countDown();pool.shutdownNow();}
    }

    @Test
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void expirationStatusCommitsBeforeReturningConflict() throws Exception {
        String token="expiry-"+UUID.randomUUID();var c=sessions.createConversation(new ConversationPrincipal(null,token));
        var tx=new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status->{var entity=conversations.findById(c.getId()).orElseThrow();entity.setExpiresAt(java.time.LocalDateTime.now().minusDays(1));});
        mvc.perform(post("/api/chatbot").cookie(new Cookie("dlm_guest",token)).contentType("application/json").content(body("request",c.getId())))
                .andExpect(status().isConflict());
        assertEquals(ConversationStatus.EXPIRED,conversations.findById(c.getId()).orElseThrow().getStatus());
    }
    void structuredTurn(String json) {
        doAnswer(invocation -> {
            String prompt = invocation.getArgument(1);
            Function<String, Object> validator = invocation.getArgument(2);
            return validator.apply(prompt.contains("DỮ LIỆU SẢN PHẨM TỪ DATABASE:")
                    ? "{\"answer\":\"Fixture response\",\"productIds\":[]}" : json);
        }).when(ai).generateAndValidate(any(), anyString(), any());
    }

    @Test void R04_gamingThenCompareBestUsesPresentedList() throws Exception {
        var cookie = new Cookie("dlm_guest", "r04-fixture");
        structuredTurn("{\"intent\":\"SEARCH\",\"searchKeyword\":\"choi game\"}");
        var first = mapper.readTree(send(null, cookie)).path("data");
        assertTrue(first.path("products").size() >= 2);
        structuredTurn("{\"intent\":\"COMPARE\",\"referenceType\":\"LAST_PRESENTED_LIST\",\"rankingCriterion\":\"GAMING\",\"resultLimit\":2}");
        var second = mapper.readTree(send(first.path("conversationId").asText(), cookie)).path("data");
        assertEquals(2, second.path("products").size());
        var presented = new java.util.HashSet<Long>();
        first.path("products").forEach(p -> presented.add(p.path("id").asLong()));
        second.path("products").forEach(p -> assertTrue(presented.contains(p.path("id").asLong())));
    }

    @Test void R03_compareThenWeatherThenRecallKeepsPair() throws Exception {
        var cookie = new Cookie("dlm_guest", "r03-fixture");
        structuredTurn("{\"intent\":\"COMPARE\",\"compareProductNames\":[\"Fixture Phone 1\",\"Fixture Phone 2\"]}");
        var first = mapper.readTree(send(null, cookie)).path("data");
        assertEquals(2, first.path("products").size());
        String id = first.path("conversationId").asText();
        structuredTurn("{\"intent\":\"OUT_OF_DOMAIN\",\"contextAction\":\"NEW_TOPIC\",\"topicName\":\"Weather\"}");
        send(id, cookie);
        structuredTurn("{\"intent\":\"RECALL\",\"referenceType\":\"LAST_COMPARED_PRODUCTS\"}");
        var recalled = mapper.readTree(send(id, cookie)).path("data");
        assertEquals(first.path("products"), recalled.path("products"));
        assertEquals(first.path("topicId"), recalled.path("topicId"));
    }
    @Autowired com.dinhluong.dlmstore.repository.ProductRepository productRepository;
    @Test void realProductSearchUsesDatabasePricesAndOnlyActiveProducts() {
        var criteria=new GeminiFilterCriteria();criteria.setIntent("SEARCH");
        criteria.setMaxPrice(new java.math.BigDecimal("10000000"));criteria.setBrands(List.of("Samsung"));
        criteria.setRam("8GB");criteria.setNetworks(List.of("5G"));criteria.setMinBattery(5000);
        var result=productSearch.search(criteria);
        assertEquals(result.aiProducts().size(),result.uiProducts().size());
        for(var product:result.aiProducts()) {
            var stored=productRepository.findById(product.getId()).orElseThrow();
            assertEquals(com.dinhluong.dlmstore.entity.Enums.ProductStatus.ACTIVE,stored.getStatus());
            assertEquals(stored.getDisplayPrice(),product.getPrice());
            assertEquals("Samsung",stored.getBrand().getName());
            assertTrue(stored.getDisplayPrice().compareTo(criteria.getMaxPrice())<=0);
            if(result.fallbackNote().isBlank()) {
                assertTrue(Boolean.TRUE.equals(stored.getSupport5g()));
                assertTrue(stored.getBatteryCapacity()>=5000);
            }
        }
    }
}
