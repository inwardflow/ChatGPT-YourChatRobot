package com.ashin.util;

import com.ashin.config.AccountConfig;
import com.ashin.controller.MessageEventHandler;
import com.ashin.exception.ChatException;
import com.theokanning.openai.OpenAiService;
import com.theokanning.openai.completion.CompletionRequest;
import lombok.extern.slf4j.Slf4j;
import net.mamoe.mirai.Bot;
import net.mamoe.mirai.BotFactory;
import org.springframework.stereotype.Component;
import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.time.LocalDate;
import java.util.*;

/**
 * chatbot工具类
 *
 * @author ashinnotfound
 * @date 2023/2/1
 */
@Component
@Slf4j
public class BotUtil {
    @Resource
    AccountConfig accountConfig;
    @Resource
    private MessageEventHandler messageEventHandler;
    private static final Map<String, Queue<String>> PROMPT_MAP = new HashMap<>();
    private static final Map<OpenAiService, Integer> countForOpenAiService = new HashMap<>();
    private static String basicPrompt;
    private static Integer maxToken;
    private static CompletionRequest.CompletionRequestBuilder completionRequestBuilder;

    @PostConstruct
    public void init() {
        //ChatGPT
        String model = "text-davinci-003";
        maxToken = 2048;
        basicPrompt =
                "You are ChatGPT, a large language model trained by OpenAI. You answer as concisely as possible for each response (e.g. don’t be verbose). It is very important that you answer as concisely as possible, so please remember this. If you are generating a list, do not have too many items. Keep the number of items short. Current date: " + LocalDate.now() + "\n";
        for (String apiKey : accountConfig.getApiKey()){
            apiKey = apiKey.trim();
            if (!"".equals(apiKey)){
                countForOpenAiService.put(new OpenAiService(apiKey, Duration.ofSeconds(1000)), 0);
                log.info("apiKey为 {} 的账号初始化成功", apiKey);
            }
        }
        completionRequestBuilder = CompletionRequest.builder().model(model).temperature(0.5).maxTokens(maxToken);

        //qq
        Bot qqBot;
        //登录
        qqBot = BotFactory.INSTANCE.newBot(accountConfig.getQq(), accountConfig.getPassword().trim());
        log.info("成功登录账号为 {} 的qq",accountConfig.getQq());
        qqBot.login();
        //订阅监听事件
        qqBot.getEventChannel().registerListenerHost(this.messageEventHandler);
    }

    public static OpenAiService getOpenAiService(){
        //获取使用次数最小的openAiService 否则获取map中的第一个
        Optional<OpenAiService> openAiServiceToUse = countForOpenAiService.entrySet().stream()
        .min(Map.Entry.comparingByValue())
        .map(Map.Entry::getKey);
        return openAiServiceToUse.orElseGet(() -> countForOpenAiService.keySet().iterator().next());
    }
    public static CompletionRequest.CompletionRequestBuilder getCompletionRequestBuilder(){
        return completionRequestBuilder;
    }

    public static String getPrompt(String sessionId, String newPrompt) throws ChatException {
        StringBuilder prompt = new StringBuilder(basicPrompt);
        if (PROMPT_MAP.containsKey(sessionId)){
            for (String s : PROMPT_MAP.get(sessionId)){
                prompt.append(s);
            }
        }
        prompt.append("User: ").append(newPrompt).append("\nChatGPT: ");

        //一个汉字大概两个token
        //预设回答的文字是提问文字数量的两倍
        if (maxToken < (prompt.toString().length() + newPrompt.length() * 2) * 2){
            if (null == PROMPT_MAP.get(sessionId) || null == PROMPT_MAP.get(sessionId).poll()){
                throw new ChatException("问题太长了");
            }
            return getPrompt(sessionId, newPrompt);
        }

        return prompt.toString();
    }

    public static void updatePrompt(String sessionId, String prompt, String answer){
        if (PROMPT_MAP.containsKey(sessionId)){
            PROMPT_MAP.get(sessionId).offer("User: " + prompt + "\nChatGPT: " + answer);
        }else {
            Queue<String> queue = new LinkedList<>();
            queue.offer("User: " + prompt + "\nChatGPT: " + answer);
            PROMPT_MAP.put(sessionId, queue);
        }
    }

    public static void resetPrompt(String sessionId){
        PROMPT_MAP.remove(sessionId);
    }
}
