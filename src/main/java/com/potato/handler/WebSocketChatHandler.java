package com.potato.handler;


import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.potato.chat.service.ChatService;
import com.potato.chat.vo.ChatMessageVO;
import com.potato.chat.vo.ChatRoomVO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/*
 * WebSocket Handler
 * TextWebSocketHandler를 상속받아 여러 클라이언트가 발송한 메세지를 처리해주는 핸들러
 * 연결 로그 확인
 */

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketChatHandler extends TextWebSocketHandler {
	
	private final ObjectMapper objectMapper;
	private final ChatService chatService;
	private final Map<WebSocketSession, String> sessions = new HashMap<>();
	
	// 소켓 연결 확인	
	@Override
	public void afterConnectionEstablished(WebSocketSession session) throws Exception {		
		log.info("connected session : {} 연결됨", session.getId());
		sessions.put(session, null);
	}
	
	// 소켓 통신 시 메세지의 전송을 다루는 부분
	@Override
	protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
		
		String payload = message.getPayload();
		log.info("payload {}", payload);
				
		// 웹소켓 클라이언트로부터 채팅 메세지를 전달 받아 채팅 메세지 객체로 변환 
		ChatMessageVO chatMessageVO = objectMapper.readValue(payload, ChatMessageVO.class);
		//
		if(sessions.get(session) == null) {
			sessions.put(session, chatMessageVO.getSenderId());
		}
		// 전달 받은 메세지에 담긴 채팅방 아이디로 발송 대상 채팅방 조회 
		ChatRoomVO chatRoomVO = chatService.findRoomById(chatMessageVO.getRoomId());
		// 해당 채팅방에 입장해있는 모든 클라이언트(Websocket Session)에게 타입에 따른 메세지 발송 
		chatRoomVO.handleActions(session, chatMessageVO, chatService);
	}
	
	// 소켓 종료 확인
	@Override
	public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
		log.info("closed session : {} 연결 끊김", session.getId());
		
		// 닫힌 웹소켓 세션으로 채팅방 조회 하기
		ChatRoomVO chatRoomVO = chatService.findRoomByUserSession(session);
		// 퇴장 메세지 객체 생성하기
		ChatMessageVO chatMessageVO = ChatMessageVO.builder()
				.messageType(ChatMessageVO.MessageType.OUT)
				.senderId(sessions.get(session))
				.build();
		// 해당 채팅방에 입장해있는 모든 클라이언트(Websocket Session)에게 퇴장 메세지 발송 
		chatRoomVO.handleActions(session, chatMessageVO, chatService);
		// 세션 그룹에서 삭제 처리
		sessions.remove(session);
	}

}
