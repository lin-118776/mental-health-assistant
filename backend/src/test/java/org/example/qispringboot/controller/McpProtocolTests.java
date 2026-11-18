package org.example.qispringboot.controller;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.qispringboot.rag.*;
import org.example.qispringboot.security.LoginUser;
import org.example.qispringboot.service.EmotionDiaryService;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class McpProtocolTests {
    ObjectMapper mapper=new ObjectMapper();
    RagService rag=mock(RagService.class);
    EmotionDiaryService diaries=mock(EmotionDiaryService.class);
    McpController mcp=new McpController(rag,diaries,mapper,"http://localhost:5173");
    LoginUser user=new LoginUser(7L,"owner",1);
    final String accept="application/json, text/event-stream";
    String request(String method,String params) {return "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\""+method+"\",\"params\":"+params+"}";}
    @Test void initializeListsReadOnlyToolsAndHandlesNotificationsAndTransportErrors() throws Exception {
        var init=mcp.call(request("initialize","{}"),user,null,accept);
        assertTrue(mapper.writeValueAsString(init.getBody()).contains("2025-03-26"));
        assertTrue(mapper.writeValueAsString(mcp.call(request("tools/list","{}"),user,null,accept).getBody()).contains("get_my_emotion_trend"));
        assertEquals(202,mcp.call("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}",user,null,accept).getStatusCode().value());
        assertEquals(405,mcp.stream().getStatusCode().value());
        assertEquals(401,mcp.call(request("ping","{}"),null,null,accept).getStatusCode().value());
        assertEquals(403,mcp.call(request("ping","{}"),user,"https://evil.example",accept).getStatusCode().value());
        assertEquals(406,mcp.call(request("ping","{}"),user,null,"application/json").getStatusCode().value());
    }
    @Test void trendToolUsesAuthenticatedUserAndRejectsIdentityOverride() throws Exception {
        mcp.call(request("tools/call","{\"name\":\"get_my_emotion_trend\",\"arguments\":{\"days\":30}}"),user,null,accept);
        verify(diaries).myTrend(7L,30);
        clearInvocations(diaries);
        for(String args:List.of("{\"userId\":999}","{\"days\":8}","{\"days\":4294967303}")) {
            var response=mcp.call(request("tools/call","{\"name\":\"get_my_emotion_trend\",\"arguments\":"+args+"}"),user,null,accept);
            assertTrue(mapper.writeValueAsString(response.getBody()).contains("-32602"));
        }
        verifyNoInteractions(diaries);
    }
    @Test void searchToolCallsRetrievalAndErrorsUseJsonRpcEnvelope() throws Exception {
        when(rag.search("睡眠")).thenReturn(List.of());
        String response=mapper.writeValueAsString(mcp.call(request("tools/call","{\"name\":\"search_knowledge\",\"arguments\":{\"query\":\"睡眠\"}}"),user,null,accept).getBody());
        assertTrue(response.contains("isError"));verify(rag).search("睡眠");
        assertTrue(mapper.writeValueAsString(mcp.call(request("unknown","{}"),user,null,accept).getBody()).contains("-32601"));
        assertEquals(400,mcp.call("{broken",user,null,accept).getStatusCode().value());
        assertEquals(400,mcp.call("[]",user,null,accept).getStatusCode().value());
    }
    @Test void callsAreRateLimitedPerUser() {
        for(int i=0;i<60;i++)assertEquals(200,mcp.call(request("ping","{}"),user,null,accept).getStatusCode().value());
        assertEquals(429,mcp.call(request("ping","{}"),user,null,accept).getStatusCode().value());
        assertEquals(200,mcp.call(request("ping","{}"),new LoginUser(8L,"other",1),null,accept).getStatusCode().value());
    }
    @Test void batchRequestsReturnOneReplyPerRequestAndOmitNotificationReplies() throws Exception {
        String body="["+request("ping","{}")+",{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}]";
        var response=mcp.call(body,user,null,accept);
        assertEquals(200,response.getStatusCode().value());
        assertEquals(1,mapper.valueToTree(response.getBody()).size());
        assertEquals(202,mcp.call("[{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}]",user,null,accept).getStatusCode().value());
    }
}
