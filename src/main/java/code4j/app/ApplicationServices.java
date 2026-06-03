package code4j.app;

import code4j.config.ProviderKind;
import code4j.config.RuntimeConfig;
import code4j.context.accounting.TokenAccountingService;
import code4j.context.compact.AutoCompactController;
import code4j.context.compact.AutoCompactPolicy;
import code4j.context.compact.CompactService;
import code4j.context.compact.ManualCompactResult;
import code4j.context.manager.ContextManager;
import code4j.context.stats.ContextStatsCalculator;
import code4j.context.stats.ModelContextWindow;
import code4j.core.event.AgentEventSink;
import code4j.core.loop.AgentLoop;
import code4j.core.loop.ModelAdapter;
import code4j.core.message.ChatMessage;
import code4j.core.message.SystemMessage;
import code4j.core.turn.AgentTurnRequest;
import code4j.core.turn.AgentTurnResult;
import code4j.model.MockModelAdapter;
import code4j.model.anthropic.AnthropicModelAdapter;
import code4j.model.anthropic.HttpAnthropicTransport;
import code4j.permissions.api.PermissionService;
import code4j.permissions.service.PromptingPermissionService;
import code4j.permissions.store.JsonPermissionStore;
import code4j.prompt.SystemPromptBuilder;
import code4j.skills.SkillDiscovery;
import code4j.skills.SkillRegistry;
import code4j.tools.builtin.*;
import code4j.tools.registry.ToolRegistry;
import code4j.tools.result.ToolResultStorage;
import code4j.tui.ConsolePermissionPromptHandler;
import code4j.workspace.WorkspacePathResolver;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Wires together all application services: tools, agent loop, model adapter,
 * context management, permissions, skills, and system prompt. This is the composition root.
 */
public final class ApplicationServices {
    private final ToolRegistry toolRegistry;
    private final AgentLoop agentLoop;
    private final ModelAdapter modelAdapter;
    private final SystemPromptBuilder systemPromptBuilder;
    private final WorkspacePathResolver workspacePathResolver;
    private final ContextManager contextManager;
    private final CompactService compactService;
    private final PermissionService permissionService;
    private final SkillRegistry skillRegistry;
    private final Path home;
    private final Path cwd;
    private final String sessionId;
    private final Optional<RuntimeConfig> runtimeConfig;

    public ApplicationServices(ToolRegistry toolRegistry, AgentLoop agentLoop, ModelAdapter modelAdapter,
                               SystemPromptBuilder systemPromptBuilder, WorkspacePathResolver workspacePathResolver,
                               ContextManager contextManager, CompactService compactService,
                               PermissionService permissionService, SkillRegistry skillRegistry,
                               Path home, Path cwd, String sessionId, Optional<RuntimeConfig> runtimeConfig) {
        this.toolRegistry = Objects.requireNonNull(toolRegistry, "toolRegistry");
        this.agentLoop = Objects.requireNonNull(agentLoop, "agentLoop");
        this.modelAdapter = Objects.requireNonNull(modelAdapter, "modelAdapter");
        this.systemPromptBuilder = Objects.requireNonNull(systemPromptBuilder, "systemPromptBuilder");
        this.workspacePathResolver = Objects.requireNonNull(workspacePathResolver, "workspacePathResolver");
        this.contextManager = Objects.requireNonNull(contextManager, "contextManager");
        this.compactService = Objects.requireNonNull(compactService, "compactService");
        this.permissionService = Objects.requireNonNull(permissionService, "permissionService");
        this.skillRegistry = Objects.requireNonNull(skillRegistry, "skillRegistry");
        this.home = Objects.requireNonNull(home, "home").toAbsolutePath().normalize();
        this.cwd = Objects.requireNonNull(cwd, "cwd");
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
        this.runtimeConfig = Objects.requireNonNull(runtimeConfig, "runtimeConfig");
    }

    public static ApplicationServices create(Path home, Path cwd, RuntimeConfig runtimeConfig, AgentEventSink eventSink) {
        return create(home, cwd, runtimeConfig, eventSink, System.in, System.out);
    }

    public static ApplicationServices create(Path home, Path cwd, RuntimeConfig runtimeConfig, AgentEventSink eventSink,
                                             InputStream stdin, OutputStream stdout) {
        Path actualHome = home.toAbsolutePath().normalize();
        Path actualCwd = cwd.toAbsolutePath().normalize();
        WorkspacePathResolver pathResolver = new WorkspacePathResolver();

        ConsolePermissionPromptHandler promptHandler = new ConsolePermissionPromptHandler(stdin, stdout);
        JsonPermissionStore permissionStore = new JsonPermissionStore(actualHome.resolve("permissions.json"));
        PermissionService permissionService = new PromptingPermissionService(promptHandler, permissionStore);

        SkillDiscovery skillDiscovery = new SkillDiscovery(actualHome, actualCwd);
        SkillRegistry skillRegistry = new SkillRegistry(skillDiscovery.discover());

        ToolResultStorage toolResultStorage = new ToolResultStorage(actualHome.resolve("tool-results"));
        BackgroundTaskManager backgroundTaskManager = new BackgroundTaskManager(toolResultStorage);

        ToolRegistry registry = createBuiltInTools(pathResolver, permissionService, skillRegistry, backgroundTaskManager);
        ModelAdapter adapter = runtimeConfig.provider() == ProviderKind.MOCK
                ? new MockModelAdapter("Mock response — task complete.")
                : new AnthropicModelAdapter(runtimeConfig, registry,
                        new HttpAnthropicTransport(java.net.http.HttpClient.newHttpClient(), runtimeConfig.providerTimeout()));
        ContextManager ctxManager = new ContextManager(toolResultStorage, 200_000, 400_000, 20_000);
        CompactService cs = new CompactService();
        AgentLoop loop = new AgentLoop(adapter, eventSink, registry, ctxManager,
                new ContextStatsCalculator(new TokenAccountingService(), new ModelContextWindow(128_000, 8_000)),
                new AutoCompactController(cs, AutoCompactPolicy.defaults()), 2);
        return new ApplicationServices(registry, loop, adapter, new SystemPromptBuilder(), pathResolver,
                ctxManager, cs, permissionService, skillRegistry, actualHome, actualCwd, UUID.randomUUID().toString(), Optional.of(runtimeConfig));
    }

    private static ToolRegistry createBuiltInTools(WorkspacePathResolver pathResolver, PermissionService permissionService,
                                                    SkillRegistry skillRegistry, BackgroundTaskManager backgroundTaskManager) {
        ToolRegistry registry = new ToolRegistry();
        ReadFilePathAccess pathAccess = ReadFilePathAccess.permissionBacked(permissionService);
        registry.register(new ReadFileTool(pathAccess, pathResolver));
        registry.register(new ListFilesTool(pathResolver));
        registry.register(new WriteFileTool(pathResolver, permissionService));
        registry.register(new GrepFilesTool(pathResolver));
        registry.register(new AskUserTool());
        registry.register(new EditFileTool(pathResolver, permissionService));
        registry.register(new ModifyFileTool(pathResolver, permissionService));
        registry.register(new PatchFileTool(pathResolver, permissionService));
        registry.register(new RunCommandTool(pathResolver, permissionService, backgroundTaskManager));
        registry.register(new LoadSkillTool(skillRegistry));
        registry.register(new WebSearchTool());
        registry.register(new WebFetchTool());
        registry.register(new CheckBackgroundTaskTool(backgroundTaskManager));
        return registry;
    }

    public AgentTurnRequest turnRequest(List<ChatMessage> messages, int maxSteps) {
        return new AgentTurnRequest(UUID.randomUUID().toString(), cwd, sessionId,
                withSystemPrompt(messages), maxSteps, Optional.empty());
    }

    public AgentTurnResult runTurn(AgentTurnRequest request) {
        return agentLoop.runTurn(new AgentTurnRequest(
                request.turnId(), request.cwd(), request.sessionId(),
                withSystemPrompt(request.messages()), request.maxSteps(),
                request.modelName(), request.cancellationToken()));
    }

    private List<ChatMessage> withSystemPrompt(List<ChatMessage> messages) {
        List<ChatMessage> result = new ArrayList<>();
        result.add(new SystemMessage(systemPromptBuilder.build(
                new SystemPromptBuilder.Input(home, cwd, toolRegistry, skillRegistry.summaries()))));
        for (ChatMessage m : messages) {
            if (!(m instanceof SystemMessage)) result.add(m);
        }
        return List.copyOf(result);
    }

    public List<ChatMessage> sessionMessages() { return List.of(); }

    public ManualCompactResult manualCompact() {
        return ManualCompactResult.skipped(List.of(), "not implemented");
    }

    public ToolRegistry toolRegistry() { return toolRegistry; }
    public AgentLoop agentLoop() { return agentLoop; }
    public ModelAdapter modelAdapter() { return modelAdapter; }
    public SystemPromptBuilder systemPromptBuilder() { return systemPromptBuilder; }
    public WorkspacePathResolver workspacePathResolver() { return workspacePathResolver; }
    public ContextManager contextManager() { return contextManager; }
    public CompactService compactService() { return compactService; }
    public PermissionService permissionService() { return permissionService; }
    public SkillRegistry skillRegistry() { return skillRegistry; }
    public Path home() { return home; }
    public Path cwd() { return cwd; }
    public String sessionId() { return sessionId; }
    public Optional<RuntimeConfig> runtimeConfig() { return runtimeConfig; }
}
