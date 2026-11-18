package org.example.qispringboot.rag;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.example.qispringboot.common.Result;
import org.example.qispringboot.security.LoginUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/rag")
public class RagController {
    private final RagService rag;
    private final org.example.qispringboot.service.CategoryCache cache;
    @org.springframework.beans.factory.annotation.Value("${app.ingestion.enabled:false}") private boolean ingestionEnabled;
    @org.springframework.beans.factory.annotation.Value("${app.cache.enabled:false}") private boolean cacheEnabled;
    public RagController(RagService rag,org.example.qispringboot.service.CategoryCache cache) {this.rag=rag;this.cache=cache;}
    @GetMapping("/admin/status") @PreAuthorize("hasRole('ADMIN')")
    public Result<?> status() {return Result.OK(java.util.Map.of("ingestionEnabled",ingestionEnabled,"cacheEnabled",cacheEnabled,"mcpEndpoint","/api/mcp"));}
    @PostMapping("/admin/cache/evict") @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> evict() {cache.evict();return Result.OK();}
    public record Input(@NotBlank @Size(max=200) String title,@Size(max=500) String sourceUrl,@NotBlank @Size(max=100000) String content) {}
    @GetMapping("/search")
    public Result<List<RagService.Source>> search(@RequestParam String query) {return Result.OK(rag.search(query));}
    @GetMapping("/admin/documents") @PreAuthorize("hasRole('ADMIN')")
    public Result<RagService.Page> page(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="10") int size) {return Result.OK(rag.page(page,size));}
    @PostMapping("/admin/documents") @PreAuthorize("hasRole('ADMIN')")
    public Result<String> create(@Valid @RequestBody Input input,@AuthenticationPrincipal LoginUser user) {return Result.OK(rag.create(input.title(),input.sourceUrl(),input.content(),user.getUserId()));}
    @PostMapping("/admin/documents/{id}/retry") @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> retry(@PathVariable String id) {rag.retry(id);return Result.OK();}
    @DeleteMapping("/admin/documents/{id}") @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> disable(@PathVariable String id) {rag.disable(id);return Result.OK();}
}
