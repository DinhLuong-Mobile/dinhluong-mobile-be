package com.dinhluong.dlmstore.chatbot.service;

import com.dinhluong.dlmstore.chatbot.dto.*;
import com.dinhluong.dlmstore.chatbot.enums.AiModel;
import com.dinhluong.dlmstore.chatbot.exception.*;
import com.dinhluong.dlmstore.dto.responses.ChatBotResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/** Network inference runs between short snapshot/commit transactions, without a database lock. */
@Service
@RequiredArgsConstructor
public class ChatbotConversationOrchestrator {
    private final ConversationMemoryService memoryService;
    private final IntentExtractionService intents;
    private final ChatbotBusinessService business;
    private final ConversationTurnStore store;
    private final ReentrantLock[] turnLocks = locks();

    public ChatBotResponse process(ConversationPrincipal principal, String conversationId, String message, AiModel model) {
        return process(principal, conversationId, message, model, null, null);
    }

    public ChatBotResponse process(ConversationPrincipal principal, String conversationId, String message, AiModel model,
            Long selectedProductId, Long clarificationId) {
        if (principal == null || message == null || message.isBlank()) throw new IllegalArgumentException("Thiếu thông tin hội thoại");
        AiModel selected = model == null ? AiModel.AUTO : model;
        String key = conversationId == null ? "new:" + principal.hashCode() : conversationId;
        ReentrantLock lock = turnLocks[Math.floorMod(key.hashCode(), turnLocks.length)];
        boolean acquired;
        try {
            acquired = lock.tryLock(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ConversationStateException("Yêu cầu bị gián đoạn; vui lòng thử lại.");
        }
        if (!acquired) throw new ConversationStateException("Một tin nhắn khác đang được xử lý trong hội thoại; vui lòng thử lại.");
        try {
            if (memoryService.expireIfOwned(conversationId, principal)) {
                throw new ConversationStateException("Phiên hội thoại đã hết hạn");
            }
            ConversationMemory memory = memoryService.load(conversationId, principal);
            try {
                var criteria = business.pendingAnswer(memory, message);
                if (selectedProductId != null || clarificationId != null) {
                    if (selectedProductId == null || selectedProductId <= 0 || clarificationId == null
                            || memory.pending() == null || !clarificationId.equals(memory.pending().id())
                            || !ProductReferenceResolver.ids(memory.pending().candidateData().get("candidateIds")).contains(selectedProductId))
                        throw new IllegalArgumentException("Invalid or stale clarification selection");
                    criteria = business.pendingAnswer(memory, "ID " + selectedProductId);
                    if (criteria == null) throw new IllegalArgumentException("Selection cannot resolve this clarification");
                }
                if (criteria == null) criteria = intents.extract(message, selected, memoryService.forIntent(memory));
                if (memory.pending() != null && selectedProductId == null
                        && PendingAnswerParser.classify(message,false) == PendingAnswerParser.Classification.NEW_REQUEST) {
                    criteria.setMessageAction(com.dinhluong.dlmstore.chatbot.enums.MessageAction.NEW_REQUEST);
                    criteria.setContextAction(com.dinhluong.dlmstore.chatbot.enums.ContextAction.NEW_TOPIC);
                }
                var relevantMemory = memoryService.withRelevantTopics(memory, criteria, principal);
                var plan = business.prepare(relevantMemory, criteria, message, selected);
                return store.commit(principal, message, selected, plan);
            } catch (AiProviderException e) {
                throw new ConversationAiException(e, memory.conversationId(), memory.activeTopicId());
            }
        } finally {
            lock.unlock();
        }
    }

    private static ReentrantLock[] locks() {
        ReentrantLock[] result = new ReentrantLock[256];
        for (int i = 0; i < result.length; i++) result[i] = new ReentrantLock();
        return result;
    }
}
