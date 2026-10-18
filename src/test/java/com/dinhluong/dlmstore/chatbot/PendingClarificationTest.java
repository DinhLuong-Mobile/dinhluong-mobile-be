package com.dinhluong.dlmstore.chatbot;

import com.dinhluong.dlmstore.chatbot.dto.ConversationMemory;
import com.dinhluong.dlmstore.chatbot.enums.*;
import com.dinhluong.dlmstore.chatbot.service.*;
import com.dinhluong.dlmstore.dto.GeminiFilterCriteria;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class PendingClarificationTest {
    @Test void pendingAnswerCannotCreateAnotherTopicEvenWhenOriginalActionWasNewTopic() {
        var topic = new ConversationMemory.Topic(60L,"fixture",Map.of(),"");
        var pending = new ConversationMemory.Pending(11L,60L,"budget?","recommend",Map.of(),1,3);
        var memory = new ConversationMemory("fixture",60L,1L,List.of(topic),List.of(),List.of(),pending);
        var c = new GeminiFilterCriteria(); c.setIntent("RECOMMEND");
        c.setMessageAction(MessageAction.ANSWER_CLARIFICATION); c.setContextAction(ContextAction.NEW_TOPIC);
        var result = new TopicResolutionService().resolve(c,memory);
        assertFalse(result.create()); assertEquals(60L,result.topic().id());
    }
    @Test void parsesVietnameseBudgetAnswers() {
        for (String text : List.of("20 triệu","Tôi có 20 triệu","Dưới 20 củ","15–20 triệu")) {
            assertEquals(0, BigDecimal.valueOf(20_000_000).compareTo(PendingAnswerParser.budget(text).get("maxPrice")));
        }
        assertEquals(0, BigDecimal.valueOf(15_000_000).compareTo(PendingAnswerParser.budget("15–20 triệu").get("minPrice")));
        assertTrue(PendingAnswerParser.budget("xin chào").isEmpty());
    }
    @Test void exactNameKeepsCopyDistinctAndNeverBreaksIdenticalNameTies() {
        var candidates = Map.of(3L,"Fixture Model 128GB",7L,"Fixture Model 128GB (Copy)");
        assertEquals(3L,PendingAnswerParser.select("Fixture Model 128GB nhé",candidates));
        assertEquals(7L,PendingAnswerParser.select("Fixture Model 128GB (Copy)",candidates));
        assertNull(PendingAnswerParser.select("another model",candidates));
        assertNull(PendingAnswerParser.select("Fixture Model",Map.of(3L,"Fixture Model",7L,"Fixture Model")));
        assertEquals(3L,PendingAnswerParser.select("Fixtur Model 128GB",candidates));
        assertEquals(7L,PendingAnswerParser.select("Copy",candidates));
        assertEquals(7L,PendingAnswerParser.select("thứ 2",new LinkedHashMap<>(new TreeMap<>(candidates))));
        assertNull(PendingAnswerParser.select("thứ 3",candidates));
    }
}
