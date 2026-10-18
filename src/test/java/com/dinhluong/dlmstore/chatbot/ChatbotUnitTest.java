package com.dinhluong.dlmstore.chatbot;

import com.dinhluong.dlmstore.chatbot.ai.*;
import com.dinhluong.dlmstore.chatbot.dto.*;
import com.dinhluong.dlmstore.chatbot.entity.*;
import com.dinhluong.dlmstore.chatbot.enums.*;
import com.dinhluong.dlmstore.chatbot.exception.*;
import com.dinhluong.dlmstore.chatbot.repository.*;
import com.dinhluong.dlmstore.chatbot.service.*;
import com.dinhluong.dlmstore.dto.GeminiFilterCriteria;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChatbotUnitTest {
    GuestSessionService guests = new GuestSessionService();
    ChatbotConversationRepository conversations = mock(ChatbotConversationRepository.class);
    ConversationSessionService sessions = new ConversationSessionService(conversations, guests);
    ConversationPrincipal guest = new ConversationPrincipal(null, "token");

    @Test void secureGuestTokensAreUniqueAndHashed() {
        String token = guests.generateToken();
        assertEquals(43, token.length());
        assertNotEquals(token, guests.generateToken());
        assertEquals(64, guests.hashToken(token).length());
        assertTrue(guests.matches(token, guests.hashToken(token)));
        assertFalse(guests.matches("other", guests.hashToken(token)));
        assertFalse(guests.matches(null, null));
        assertThrows(IllegalArgumentException.class, () -> guests.hashToken(" "));
    }
    @Test void guestConversationStoresOnlyHashAndExpiresInSevenDays() {
        when(conversations.save(any())).thenAnswer(i -> i.getArgument(0));
        var c = sessions.createConversation(guest);
        assertNull(c.getUserId());
        assertTrue(guests.matches("token", c.getGuestTokenHash()));
        assertEquals(c.getCreatedAt().plusDays(7), c.getExpiresAt());
    }
    @Test void authenticatedConversationHasNoGuestHash() {
        when(conversations.save(any())).thenAnswer(i -> i.getArgument(0));
        var c = sessions.createConversation(new ConversationPrincipal(7L, "ignored"));
        assertEquals(7L, c.getUserId());
        assertNull(c.getGuestTokenHash());
    }
    @Test void ownershipCannotBeBypassed() {
        var c = ChatbotConversation.builder().id("c").userId(7L).status(ConversationStatus.ACTIVE).build();
        when(conversations.findForUpdate("c")).thenReturn(Optional.of(c));
        assertThrows(SecurityException.class, () -> sessions.getActiveConversation("c", guest));
        assertThrows(SecurityException.class, () -> sessions.getActiveConversation("c", new ConversationPrincipal(8L, null)));
        assertSame(c, sessions.getActiveConversation("c", new ConversationPrincipal(7L, null)));
    }
    @Test void wrongGuestCannotContinueAndClosedOrExpiredAreRejected() {
        var c = ChatbotConversation.builder().id("c").guestTokenHash(guests.hashToken("token"))
                .status(ConversationStatus.ACTIVE).build();
        when(conversations.findForUpdate("c")).thenReturn(Optional.of(c));
        assertThrows(SecurityException.class, () -> sessions.getActiveConversation("c", new ConversationPrincipal(null,"wrong")));
        assertSame(c, sessions.getOrCreateConversation("c", guest));
        c.setStatus(ConversationStatus.CLOSED);
        assertThrows(IllegalStateException.class, () -> sessions.getActiveConversation("c", guest));
        c.setStatus(ConversationStatus.ACTIVE);c.setExpiresAt(LocalDateTime.now().minusSeconds(1));
        assertThrows(IllegalStateException.class, () -> sessions.getActiveConversation("c", guest));
    }
    @Test void parserAcceptsFencesAndRejectsMalformedJson() {
        var parser = new AiJsonParser(new ObjectMapper());
        assertEquals("CHAT", parser.parse("```json\n{\"intent\":\"CHAT\"}\n```", GeminiFilterCriteria.class).getIntent());
        assertThrows(IllegalArgumentException.class, () -> parser.parse("bad", GeminiFilterCriteria.class));
    }
    @Test void autoContinuesAfterPermanentProviderFailure() {
        AiClient first = mock(AiClient.class), second = mock(AiClient.class);
        when(first.getProviderName()).thenReturn("first");when(second.getProviderName()).thenReturn("second");
        when(first.generate("prompt")).thenThrow(new AiProviderException("missing key", false, null));
        when(second.generate("prompt")).thenReturn("ok");
        assertEquals("ok", new FallbackAiClient(List.of(first,second), List.of("first","second")).generate("prompt"));
    }
    @Test void explicitProviderInvalidJsonIsProviderFailure() {
        var openai = mock(OpenAiClient.class);
        when(openai.generate("p")).thenReturn("bad");
        var router = new AiRouter(openai, mock(GeminiAiClient.class), mock(OllamaAiClient.class), mock(FallbackAiClient.class));
        assertThrows(AiProviderException.class, () -> router.generateAndValidate(AiModel.OPENAI, "p", raw -> {
            throw new IllegalArgumentException("bad JSON");
        }));
    }
    @Test void serviceRejectsBlankAndDefaultsModel() {
        var orchestrator = mock(ChatbotConversationOrchestrator.class);
        var service = new ChatbotService(orchestrator);
        assertThrows(IllegalArgumentException.class, () -> service.processUserMessage(guest,null," ",null));
        service.processUserMessage(guest,null," hi ",null);
        verify(orchestrator).process(guest,null,"hi",AiModel.AUTO);
    }

    @Test void contextMergesClearsAndReturnsDefensiveLists() {
        var repo=mock(ChatbotTopicRepository.class);var session=mock(ConversationSessionService.class);
        var service=new ConversationContextService(repo,session);
        var topic=ChatbotTopic.builder().id(1L).conversationId("c")
                .contextData(new HashMap<>(Map.of("brands",new ArrayList<>(List.of("Samsung")),"maxPrice",10000000))).build();
        when(repo.findByIdAndConversationId(1L,"c")).thenReturn(Optional.of(topic));
        var result=service.updateContext("c",guest,1L,new ConversationContextUpdate(Map.of("ram","8GB"),List.of(),List.of(3L,2L)));
        assertEquals(List.of("Samsung"),result.get("brands"));assertEquals("8GB",result.get("ram"));
        ((List<?>)result.get("brands")).clear();assertEquals(List.of("Samsung"),topic.getContextData().get("brands"));
        service.updateContext("c",guest,1L,new ConversationContextUpdate(Map.of(),List.of("brands"),null));
        assertFalse(topic.getContextData().containsKey("brands"));
        assertEquals(2L,service.getProductIdByPosition("c",guest,1L,2).orElseThrow());
        assertThrows(IllegalArgumentException.class,()->service.updateContext("c",guest,1L,
                new ConversationContextUpdate(Map.of("minPrice",20000000),List.of(),null)));
    }
    @Test void topicsCannotCrossConversationBoundary() {
        var repo=mock(ChatbotTopicRepository.class);var session=mock(ConversationSessionService.class);
        when(session.getActiveConversation("c",guest)).thenReturn(ChatbotConversation.builder().id("c").build());
        var service=new ConversationTopicService(repo,session);
        assertThrows(IllegalArgumentException.class,()->service.switchTopic("c",guest,99L));
    }
    @Test void clarificationCannotResolveCancelledOrExpired() {
        var repo=mock(ChatbotClarificationRepository.class);var topics=mock(ChatbotTopicRepository.class);
        var session=mock(ConversationSessionService.class);var service=new ClarificationService(repo,topics,session);
        when(topics.findByIdAndConversationId(1L,"c")).thenReturn(Optional.of(new ChatbotTopic()));
        var question=ChatbotClarification.builder().id(1L).status(ClarificationStatus.CANCELLED).build();
        when(repo.findByIdAndConversationIdAndTopicId(1L,"c",1L)).thenReturn(Optional.of(question));
        assertThrows(IllegalStateException.class,()->service.resolve("c",guest,1L,1L,Map.of("answer","yes")));
        question.setStatus(ClarificationStatus.PENDING);question.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        assertEquals(ClarificationStatus.EXPIRED,service.resolve("c",guest,1L,1L,Map.of("answer","yes")).getStatus());
        assertNull(question.getResolvedValue());
    }

    @Test void unexpectedFailureProduces500WithOriginalFallbackShape() throws Exception {
        var service=mock(ChatbotService.class);
        when(service.processUserMessage(any(),any(),anyString(),any())).thenThrow(
                new IllegalStateException("test unexpected failure"));
        var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(
                new com.dinhluong.dlmstore.chatbot.controller.ChatbotController(service,guests))
                .setControllerAdvice(new ChatbotExceptionHandler()).build();
        var response=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/chatbot")
                .contentType("application/json").content("{\"message\":\"hi\"}"))
                .andReturn().getResponse();
        assertEquals(500,response.getStatus());
        var json=new ObjectMapper().readTree(response.getContentAsString());
        assertTrue(json.path("conversationId").isNull());assertTrue(json.path("products").isArray());
    }
    @Test void providerHttpErrorsAreClassifiedWithoutLiveRequests() {
        for(var status:List.of(org.springframework.http.HttpStatus.UNAUTHORIZED,
                org.springframework.http.HttpStatus.FORBIDDEN,org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,
                org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE)) {
            var rest=new org.springframework.web.client.RestTemplate();
            var server=org.springframework.test.web.client.MockRestServiceServer.bindTo(rest).build();
            var client=new OpenAiClient(rest,new ObjectMapper());
            org.springframework.test.util.ReflectionTestUtils.setField(client,"apiKey","test-key");
            org.springframework.test.util.ReflectionTestUtils.setField(client,"apiUrl","https://provider.example/responses");
            org.springframework.test.util.ReflectionTestUtils.setField(client,"model","test-model");
            server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo("https://provider.example/responses"))
                    .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withStatus(status));
            var error=assertThrows(AiProviderException.class,()->client.generate("hello"));
            assertEquals(status.value()==429 || status.is5xxServerError(),error.isRetryable());server.verify();
        }
    }
    @Test void hallucinatedProductIdsFallBackOnlyToDatabaseCandidates() {
        var openai=mock(OpenAiClient.class);
        when(openai.generate(anyString())).thenReturn("{\"answer\":\"bad\",\"productIds\":[999]}");
        var router=new AiRouter(openai,mock(GeminiAiClient.class),mock(OllamaAiClient.class),mock(FallbackAiClient.class));
        var mapper=new ObjectMapper();var service=new AiResponseService(new AiJsonParser(mapper),mapper,router);
        var product=com.dinhluong.dlmstore.dto.ProductAiDto.builder().id(1L).name("Database phone").build();
        var result=service.generateProductResponse("find","SEARCH",List.of(product),"",AiModel.OPENAI);
        assertEquals(List.of(1L),result.getProductIds());
    }

    @Test void routerDelegatesEachModelAndDefaultsToAuto() {
        var openai=mock(OpenAiClient.class);var gemini=mock(GeminiAiClient.class);var ollama=mock(OllamaAiClient.class);
        var fallback=mock(FallbackAiClient.class);var router=new AiRouter(openai,gemini,ollama,fallback);
        when(openai.generate("p")).thenReturn("o");when(gemini.generate("p")).thenReturn("g");
        when(ollama.generate("p")).thenReturn("l");when(fallback.generate("p")).thenReturn("a");
        assertEquals("o",router.generate(AiModel.OPENAI,"p"));assertEquals("g",router.generate(AiModel.GEMINI,"p"));
        assertEquals("l",router.generate(AiModel.OLLAMA,"p"));assertEquals("a",router.generate(null,"p"));
    }

    @Test void intentValidationRejectsInvalidFiltersAndNormalizesNullLists() {
        var openai=mock(OpenAiClient.class);var mapper=new ObjectMapper();
        var router=new AiRouter(openai,mock(GeminiAiClient.class),mock(OllamaAiClient.class),mock(FallbackAiClient.class));
        var service=new IntentExtractionService(new com.dinhluong.dlmstore.chatbot.prompt.IntentPromptBuilder(mapper),new AiJsonParser(mapper),router);
        when(openai.generate(anyString())).thenReturn("{\"intent\":\" search \",\"compareProductNames\":null,\"clearFields\":null}");
        var criteria=service.extract("find",AiModel.OPENAI);
        assertEquals("SEARCH",criteria.getIntent());assertEquals(List.of(),criteria.getClearFields());
        assertEquals(ContextAction.CONTINUE,criteria.getContextAction());
        for(String invalid:List.of("{\"intent\":\"SEARCH\",\"minBattery\":0}",
                "{\"intent\":\"SEARCH\",\"minPrice\":200,\"maxPrice\":100}",
                "{\"intent\":\"UNKNOWN\"}", "{\"intent\":\"SEARCH\",\"brands\":[null]}")) {
            when(openai.generate(anyString())).thenReturn(invalid);
            assertThrows(AiProviderException.class,()->service.extract("find",AiModel.OPENAI));
        }
    }

    @Test void databaseFailureHasSeparateErrorMessage() {
        var handler=new ChatbotExceptionHandler();
        var response=handler.handleDatabaseError(new org.springframework.dao.DataAccessResourceFailureException("test offline"));
        assertEquals(500,response.getStatusCode().value());assertNotNull(response.getBody());
        assertNotEquals(handler.handleUnexpectedError(new RuntimeException("test")).getBody().getAnswer(),response.getBody().getAnswer());
    }
}
