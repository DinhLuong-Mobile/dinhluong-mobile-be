package com.dinhluong.dlmstore.chatbot;

import com.dinhluong.dlmstore.chatbot.dto.*;
import com.dinhluong.dlmstore.chatbot.enums.*;
import com.dinhluong.dlmstore.chatbot.service.*;
import com.dinhluong.dlmstore.chatbot.ai.*;
import com.dinhluong.dlmstore.chatbot.exception.AiProviderException;
import com.dinhluong.dlmstore.dto.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MemoryAndRankingTest {
    ConversationMemory memory(List<Long> ids) {
        return new ConversationMemory("fixture", 7L, 1L,
                List.of(new ConversationMemory.Topic(7L,"fixture",Map.of("lastProductIds",ids),"")),List.of(),List.of(),null);
    }
    @Test void positionsUseDisplayOrderAndInvalidPositionClarifies() {
        var resolver=new ProductReferenceResolver();var memory=memory(List.of(42L,9L,73L));
        var c=new GeminiFilterCriteria();c.setIntent("DETAIL");c.setReferenceType(ReferenceType.PRODUCT_BY_POSITION);c.setProductPositions(List.of(2));
        assertEquals(List.of(9L),resolver.resolve(c,memory,memory.activeTopic()).ids());
        c.setProductPositions(List.of(4));assertNotNull(resolver.resolve(c,memory,memory.activeTopic()).question());
    }
    @Test void missingHistoryIsNotAnEmptySearch() {
        var c=new GeminiFilterCriteria();c.setIntent("RECALL");c.setReferenceType(ReferenceType.LAST_COMPARED_PRODUCTS);
        assertNotNull(new ProductReferenceResolver().resolve(c,memory(List.of()),null).question());
    }
    @Test void rankingUsesPricesAndRemovesNearDuplicateStorageModels() {
        var a=phone(4,"Model A 128GB",9);var a2=phone(3,"Model A 256GB",10);var b=phone(2,"Model B",11);
        var result=new ProductRankingService().rank(List.of(a,a2,b),RankingCriterion.PRICE_ASC,2);
        assertEquals(List.of(4L,2L),result.ids());assertNull(result.question());
    }
    @Test void gamingUsesDocumentedHardwareProxyAndCameraInsufficiencyClarifies() {
        var a=phone(9,"Model A",10);a.setSpecs(Map.of("ram","8GB","refreshRate","90 Hz","battery","5000 mAh"));
        var b=phone(8,"Model B",9);b.setSpecs(Map.of("ram","12GB","refreshRate","120 Hz","battery","5000 mAh"));
        var ranking=new ProductRankingService();assertEquals(List.of(8L,9L),ranking.rank(List.of(a,b),RankingCriterion.GAMING,2).ids());
        assertNotNull(ranking.rank(List.of(a,b),RankingCriterion.CAMERA,2).question());
        a.setSpecs(Map.of());b.setSpecs(Map.of());assertNotNull(ranking.rank(List.of(a,b),RankingCriterion.GAMING,2).question());
    }
    @Test void filterModesAndClearAreDeterministic() {
        var merger=new FilterMergeService(new ObjectMapper());
        var previous=Map.<String,Object>of("brands",List.of("Samsung"),"ram","8GB","maxPrice",10000000,"lastProductIds",List.of(9L));
        var c=new GeminiFilterCriteria();c.setClearFields(List.of("brands"));c.setMaxPrice(BigDecimal.valueOf(15000000));
        var merged=merger.merge(previous,c,false);assertFalse(merged.containsKey("brands"));assertEquals("8GB",merged.get("ram"));
        c.setFilterMode(FilterMode.RESET);c.setMaxPrice(null);merged=merger.merge(previous,c,false);
        assertFalse(merged.containsKey("ram"));assertEquals(List.of(9L),merged.get("lastProductIds"));
    }
    @Test void smallTalkAndOutOfDomainCannotCreateNewTopic() {
        var resolver=new TopicResolutionService();var c=new GeminiFilterCriteria();c.setContextAction(ContextAction.NEW_TOPIC);
        for(String intent:List.of("CHAT","OUT_OF_DOMAIN","RECALL")) {
            c.setIntent(intent);assertFalse(resolver.resolve(c,memory(List.of())).create());
        }
    }
    @Test void localAiBudgetPreventsUnboundedCallsAndReleasesCapacityOnFailure() {
        var guard=new AiUsageGuard();
        org.springframework.test.util.ReflectionTestUtils.setField(guard,"maxPerMinute",1);
        assertThrows(RuntimeException.class,()->guard.call(()->{throw new RuntimeException("fixture");}));
        assertThrows(AiProviderException.class,()->guard.call(()->"not called"));
    }
    @Test void providerTimeoutAndGeminiKeyRotationAreBounded() {
        var rest=new org.springframework.web.client.RestTemplate();
        var server=org.springframework.test.web.client.MockRestServiceServer.bindTo(rest).build();
        var properties=new com.dinhluong.dlmstore.config.GeminiProperties();properties.setUrl("https://provider.example/generate");
        properties.setKeys(List.of("fixture-key-a","fixture-key-b","fixture-key-c","fixture-key-d"));
        var client=new GeminiAiClient(rest,new ObjectMapper(),properties);
        server.expect(org.springframework.test.web.client.ExpectedCount.times(2),
                org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo(properties.getUrl()))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withStatus(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS));
        assertTrue(assertThrows(AiProviderException.class,()->client.generate("fixture prompt")).isRetryable());server.verify();
        server.reset();server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo(properties.getUrl()))
                .andRespond(request->{throw new org.springframework.web.client.ResourceAccessException("fixture timeout");});
        assertTrue(assertThrows(AiProviderException.class,()->client.generate("fixture prompt")).isRetryable());server.verify();
    }
    @Test void factualComparisonEscapesMarkdownAndUsesOnlyProvidedPrices() {
        var a=phone(11,"Model [unsafe](javascript:alert)",10);var b=phone(12,"Model B",15);
        var cards=List.of(com.dinhluong.dlmstore.dto.ChatProductDto.builder().id(11L).name(a.getName()).price(a.getPrice()).build(),
                com.dinhluong.dlmstore.dto.ChatProductDto.builder().id(12L).name(b.getName()).price(b.getPrice()).build());
        var response=new GroundedProductResponseService().render("COMPARE",
                new ChatbotSearchResult(List.of(a,b),cards,""),"");
        assertTrue(response.getAnswer().contains("10 đ"));assertTrue(response.getAnswer().contains("15 đ"));
        assertTrue(response.getAnswer().contains("5 đ"));assertTrue(response.getAnswer().contains("\\[unsafe\\]"));
    }
    private static ProductAiDto phone(long id,String name,int price) {
        return ProductAiDto.builder().id(id).name(name).price(BigDecimal.valueOf(price)).specs(Map.of()).build();
    }
}
