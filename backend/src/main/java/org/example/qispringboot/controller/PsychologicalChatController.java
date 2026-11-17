package org.example.qispringboot.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.example.qispringboot.DTO.command.AiChatCommandDTO;
import org.example.qispringboot.DTO.command.ChatMessageCommandDTO;
import org.example.qispringboot.DTO.command.SessionStartDTO;
import org.example.qispringboot.DTO.command.SessionRenameDTO;
import org.example.qispringboot.DTO.response.ChatReplyResponseDTO;
import org.example.qispringboot.DTO.response.ChatSessionSummaryResponseDTO;
import org.example.qispringboot.common.Result;
import org.example.qispringboot.entity.ChatMessage;
import org.example.qispringboot.entity.ChatSession;
import org.example.qispringboot.service.ChatService;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * 心理咨询控制器
 * 提供开始咨询会话、发起咨询、SSE流式AI对话、会话管理等接口
 */
@RestController
@RequestMapping("/api/psychological-chat")
public class PsychologicalChatController {

    @Resource
    private ChatService chatService;

    /**
     * 开始咨询会话接口
     * 自动校验当前登录用户账号状态，创建新的会话
     *
     * @param startDTO 会话标题（可选）
     * @return 新会话ID
     */
    @PostMapping("/session/start")
    public Result<Long> startSession(@RequestBody(required = false) SessionStartDTO startDTO) {
        String title = startDTO != null ? startDTO.getTitle() : null;
        return Result.OK(chatService.startSession(title));
    }

    /**
     * 发起咨询接口（简单模式）
     * 未传sessionId时自动新建会话，用户问题与AI回复分别存入聊天消息表
     *
     * @param commandDTO 咨询请求参数（消息内容）
     * @return 回复响应（会话ID、消息ID与AI回复内容）
     */
    @PostMapping("/chat")
    public Result<ChatReplyResponseDTO> chat(@Valid @RequestBody ChatMessageCommandDTO commandDTO) {
        return Result.OK(chatService.chat(commandDTO));
    }

    /**
     * 流式心理咨询接口（SSE）
     * 响应类型为TEXT_EVENT_STREAM，通过Server-Sent Events持续向前端推送AI分段内容；
     * 流结束前发送一个 event:done 事件，前端据此结束本次流式对话
     *
     * @param commandDTO 流式咨询请求参数
     * @return SSE事件流（每段data为JSON：{"sessionId":..,"content":".."}）
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> stream(@Valid @RequestBody AiChatCommandDTO commandDTO) {
        // 调用Service层流式方法，将每个分段包装为SSE事件推送，末尾追加done事件
        return chatService.streamChat(commandDTO)
                .map(segment -> ServerSentEvent.builder(segment).build())
                .concatWith(Flux.just(ServerSentEvent.<String>builder().event("done").data("").build()));
    }

    /**
     * 分页查询当前登录用户的会话列表
     *
     * @param pageNum  页码（兼容page）
     * @param pageSize 每页条数（兼容size）
     * @return 分页数据（records/total）
     */
    @GetMapping("/sessions")
    public Result<Page<ChatSessionSummaryResponseDTO>> sessions(
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "pageSize", required = false) Long pageSize,
            @RequestParam(value = "page", required = false) Long page,
            @RequestParam(value = "size", required = false) Long size) {
        long current = pageNum != null ? pageNum : (page != null ? page : 1);
        long pageSizeValue = pageSize != null ? pageSize : (size != null ? size : 10);
        return Result.OK(chatService.pageSessions(current, pageSizeValue));
    }

    /**
     * 查询会话消息列表
     *
     * @param sessionId 会话ID
     * @return 消息列表
     */
    @GetMapping("/sessions/{sessionId}/messages")
    public Result<List<ChatMessage>> messages(@PathVariable Long sessionId) {
        return Result.OK(chatService.listMessages(sessionId));
    }

    /**
     * 删除会话（同时删除其消息）
     *
     * @param sessionId 会话ID
     * @return 统一响应
     */
    @PutMapping("/sessions/{sessionId}/title")
    public Result<Void> renameSession(@PathVariable Long sessionId, @Valid @RequestBody SessionRenameDTO dto) {
        chatService.renameSession(sessionId, dto.getTitle());
        return Result.OK();
    }

    @DeleteMapping("/sessions/{sessionId}")
    public Result<Void> deleteSession(@PathVariable Long sessionId) {
        chatService.deleteSession(sessionId);
        return Result.OK();
    }

    /**
     * 获取会话情绪分析
     *
     * @param sessionId 会话ID
     * @return 情绪分析结果
     */
    @GetMapping("/session/{sessionId}/emotion")
    public Result<Map<String, Object>> emotion(@PathVariable Long sessionId) {
        return Result.OK(chatService.getSessionEmotion(sessionId));
    }
}
