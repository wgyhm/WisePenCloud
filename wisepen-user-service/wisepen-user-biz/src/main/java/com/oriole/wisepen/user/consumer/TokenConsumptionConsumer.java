package com.oriole.wisepen.user.consumer;

import com.oriole.wisepen.user.api.domain.mq.TokenConsumptionMessage;
import com.oriole.wisepen.user.service.IWalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import static com.oriole.wisepen.user.api.constant.MqTopicConstants.TOPIC_TOKEN_CONSUMPTION;

@Slf4j
@Component
@RequiredArgsConstructor
public class TokenConsumptionConsumer {

	private final IWalletService walletService;

	@KafkaListener(topics = TOPIC_TOKEN_CONSUMPTION, groupId = "wisepen-user-token-consumption-group")
	public void onTokenConsumption(TokenConsumptionMessage message) {
		log.debug("接收到 Token 消耗事件 TraceId={}", message.getTraceId());
		walletService.consumptionToken(message);
		log.debug("已处理 Token 消耗事件 TraceId={}", message.getTraceId());
	}
}
