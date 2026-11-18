package org.example.qispringboot.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.qispringboot.security.LoginUser;
import org.example.qispringboot.service.EmotionDiaryService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** MCP 2025-03-26，无状态 Streamable HTTP，仅提供只读工具。复用每次请求的JWT身份。 */
@RestController
@RequestMapping("/api/mcp")
public class McpController {
    private final RagService rag;
    private final EmotionDiaryService diaries;
    private final ObjectMapper mapper;
    private final Set<String> origins;
    private final ConcurrentHashMap<Long,long[]> rate=new ConcurrentHashMap<>();
    public McpController(RagService rag,EmotionDiaryService diaries,ObjectMapper mapper,
            @Value("${app.mcp.allowed-origins:http://localhost:5173,http://127.0.0.1:5173,http://localhost:8080,http://127.0.0.1:8080}") String origins) {
        this.rag=rag;this.diaries=diaries;this.mapper=mapper;this.origins=new HashSet<>(Arrays.asList(origins.split(",")));
    }
    private static Map<String,Object> error(Object id,int code,String message) {
        var result=new LinkedHashMap<String,Object>();result.put("jsonrpc","2.0");result.put("id",id);
        result.put("error",Map.of("code",code,"message",message));return result;
    }
    private static Map<String,Object> ok(Object id,Object value) {return Map.of("jsonrpc","2.0","id",id,"result",value);}
    private synchronized boolean allow(Long user) {
        long now=System.currentTimeMillis();
        rate.entrySet().removeIf(entry->now-entry.getValue()[0]>=60000);
        if(rate.size()>10000 && !rate.containsKey(user)) return false;
        var count=rate.computeIfAbsent(user,key->new long[]{now,0});return ++count[1]<=60;
    }
    @GetMapping public ResponseEntity<Void> stream() {return ResponseEntity.status(405).build();}
    @PostMapping(produces="application/json")
    public ResponseEntity<?> call(@RequestBody String body,@AuthenticationPrincipal LoginUser user,
            @RequestHeader(value="Origin",required=false) String origin,
            @RequestHeader(value="Accept",defaultValue="") String accept) {
        if(user==null) return ResponseEntity.status(401).body(error(null,-32001,"需要登录"));
        if(origin!=null && !origins.contains(origin)) return ResponseEntity.status(403).body(error(null,-32001,"不允许的Origin"));
        if(!accept.contains("application/json") || !accept.contains("text/event-stream")) return ResponseEntity.status(406).body(error(null,-32600,"Accept须包含application/json及text/event-stream"));
        if(!allow(user.getUserId())) return ResponseEntity.status(429).body(error(null,-32000,"请求过于频繁"));
        if(body.length()>65536) return ResponseEntity.status(413).body(error(null,-32600,"请求过大"));
        JsonNode request;
        try {request=mapper.readTree(body);}
        catch(Exception e) {return ResponseEntity.badRequest().body(error(null,-32700,"JSON解析失败"));}
        if(request!=null && request.isArray()) {
            if(request.isEmpty() || request.size()>20) return ResponseEntity.badRequest().body(error(null,-32600,"批量请求须为1–20项"));
            List<Object> replies=new ArrayList<>();
            for(JsonNode item:request) {
                if(!item.isObject()) {replies.add(error(null,-32600,"批量请求项须为对象"));continue;}
                var response=call(item.toString(),user,origin,accept);
                if(response.getBody()!=null) replies.add(response.getBody());
            }
            return replies.isEmpty() ? ResponseEntity.accepted().build() : ResponseEntity.ok(replies);
        }
        if(request==null || !request.isObject() || !"2.0".equals(request.path("jsonrpc").asText()) || !request.path("method").isTextual())
            return ResponseEntity.badRequest().body(error(null,-32600,"无效请求"));
        var id=request.get("id");
        if(id==null) return ResponseEntity.accepted().build();
        if(!id.isTextual() && !id.isIntegralNumber()) return ResponseEntity.badRequest().body(error(null,-32600,"无效ID"));
        try {
            Object result=switch(request.path("method").asText()) {
                case "initialize" -> Map.of("protocolVersion","2025-03-26","capabilities",Map.of("tools",Map.of("listChanged",false)),"serverInfo",Map.of("name","mental-health-assistant","version","1.0.0"));
                case "ping" -> Map.of();
                case "tools/list" -> Map.of("tools",List.of(
                    Map.of("name","search_knowledge","description","检索已入库知识资料，不提供诊断", "inputSchema",Map.of("type","object","properties",Map.of("query",Map.of("type","string","minLength",1,"maxLength",4000)),"required",List.of("query"),"additionalProperties",false)),
                    Map.of("name","get_my_emotion_trend","description","仅查询当前登录用户自己的7天或30天情绪趋势", "inputSchema",Map.of("type","object","properties",Map.of("days",Map.of("type","integer","enum",List.of(7,30))),"additionalProperties",false))));
                case "tools/call" -> invoke(request.path("params"),user);
                default -> null;
            };
            return ResponseEntity.ok(result==null ? error(id,-32601,"未知方法") : ok(id,result));
        } catch(IllegalArgumentException e) {return ResponseEntity.ok(error(id,-32602,e.getMessage()));}
        catch(Exception e) {return ResponseEntity.ok(ok(id,Map.of("isError",true,"content",List.of(Map.of("type","text","text","工具暂不可用，请稍后重试")))));}
    }
    private Object invoke(JsonNode params,LoginUser user) throws Exception {
        String name=params.path("name").asText();
        JsonNode args=params.path("arguments");
        if(args.isMissingNode()) args=mapper.createObjectNode();
        if(!args.isObject()) throw new IllegalArgumentException("arguments须为对象");
        Object data;
        if("search_knowledge".equals(name)) {
            if(args.size()!=1 || !args.path("query").isTextual() || args.path("query").asText().isBlank() || args.path("query").asText().length()>4000) throw new IllegalArgumentException("仅接受query字段，长度1–4000");
            data=rag.search(args.path("query").asText());
        } else if("get_my_emotion_trend".equals(name)) {
            if(args.size()>1 || (args.size()==1 && !args.has("days")) || (args.has("days") && (!args.get("days").isIntegralNumber() || !args.get("days").canConvertToInt()))) throw new IllegalArgumentException("仅接受days字段");
            int days=args.path("days").asInt(7);
            if(days!=7 && days!=30) throw new IllegalArgumentException("days仅支持7或30");
            data=diaries.myTrend(user.getUserId(),days);
        } else throw new IllegalArgumentException("未知工具");
        org.slf4j.LoggerFactory.getLogger(McpController.class).info("MCP只读工具调用 userId={} tool={}",user.getUserId(),name);
        return Map.of("isError",false,"content",List.of(Map.of("type","text","text",mapper.writeValueAsString(data))));
    }
}
