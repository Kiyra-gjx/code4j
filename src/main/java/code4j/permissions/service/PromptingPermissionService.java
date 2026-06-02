package code4j.permissions.service;

import code4j.permissions.api.PermissionPromptHandler;
import code4j.permissions.api.PermissionService;
import code4j.permissions.model.*;
import code4j.permissions.store.PermissionResourceKey;
import code4j.permissions.store.PermissionStore;
import code4j.permissions.store.PermissionStoreDecision;
import code4j.permissions.store.PermissionStoreEntry;

import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

public final class PromptingPermissionService implements PermissionService {
    private final PermissionPromptHandler promptHandler;
    private final PermissionStore store;
    private final Map<String, Set<PermissionResourceKey>> turnAllows = new HashMap<>();

    public PromptingPermissionService(PermissionPromptHandler promptHandler) {
        this(promptHandler, PermissionStore.none());
    }

    public PromptingPermissionService(PermissionPromptHandler promptHandler, PermissionStore store) {
        this.promptHandler = Objects.requireNonNull(promptHandler, "promptHandler");
        this.store = Objects.requireNonNull(store, "store");
    }

    @Override
    public PermissionGrant ensurePath(Path path, PathIntent intent, PermissionContext context) {
        PermissionResource resource = new PermissionResource.PathResource(path, intent);
        return ensure(request(PermissionRequestKind.PATH, resource, "Allow path " + intent + " access", context), PermissionKind.PATH);
    }

    @Override
    public PermissionGrant ensureCommand(CommandSignature signature, CommandClassification classification, PermissionContext context) {
        PermissionResource resource = new PermissionResource.CommandResource(signature, classification);
        return ensure(request(PermissionRequestKind.COMMAND, resource, "Allow command execution", context), PermissionKind.COMMAND);
    }

    @Override
    public PermissionGrant ensureEdit(PermissionResource.EditResource resource, PermissionContext context) {
        return ensure(request(PermissionRequestKind.EDIT, resource, "Allow file edit", context), PermissionKind.EDIT);
    }

    @Override
    public PermissionGrant ensureMcpTool(PermissionResource.McpToolResource resource, PermissionContext context) {
        return ensure(request(PermissionRequestKind.MCP_TOOL, resource, "Allow MCP tool call", context), PermissionKind.MCP_TOOL);
    }

    private PermissionGrant ensure(PermissionRequest request, PermissionKind kind) {
        PermissionResourceKey key = PermissionResourceKey.from(request.resource());
        Optional<PermissionStoreEntry> stored = store.find(request.resource());
        if (stored.isPresent() && stored.orElseThrow().decision() == PermissionStoreDecision.DENY) {
            throw new PermissionDeniedException(request, Optional.empty(), Optional.empty());
        }
        if (stored.isPresent() && stored.orElseThrow().decision() == PermissionStoreDecision.ALLOW) {
            return grant(kind, request.resource(), PermissionGrantScope.ALWAYS, PermissionPersistence.USER);
        }
        if (turnAllowed(request.context(), key)) {
            return grant(kind, request.resource(), PermissionGrantScope.TURN, PermissionPersistence.MEMORY);
        }

        PermissionPromptResult result = Objects.requireNonNull(promptHandler.prompt(request), "prompt result");
        PermissionChoice choice = choiceFor(request, result);
        if (!isAllow(choice.decision())) {
            if (choice.decision() == PermissionDecision.DENY_ALWAYS) {
                store.save(new PermissionStoreEntry(PermissionStoreDecision.DENY, kind, key, Instant.now()));
            }
            throw new PermissionDeniedException(request, Optional.of(choice.key()), result.feedback());
        }
        if (choice.decision() == PermissionDecision.ALLOW_ALWAYS) {
            store.save(new PermissionStoreEntry(PermissionStoreDecision.ALLOW, kind, key, Instant.now()));
        }
        if (choice.decision() == PermissionDecision.ALLOW_TURN) {
            request.context().turnId().ifPresent(tid ->
                    turnAllows.computeIfAbsent(tid, ignored -> new HashSet<>()).add(key));
        }
        return grant(kind, request.resource(), scopeFor(choice.decision()), persistenceFor(choice.decision()));
    }

    @Override
    public synchronized void beginTurn(String turnId) {
        turnAllows.computeIfAbsent(requireText(turnId, "turnId"), ignored -> new HashSet<>());
    }

    @Override
    public synchronized void endTurn(String turnId) {
        turnAllows.remove(requireText(turnId, "turnId"));
    }

    private synchronized boolean turnAllowed(PermissionContext ctx, PermissionResourceKey key) {
        return ctx.turnId().map(turnAllows::get).map(keys -> keys.contains(key)).orElse(false);
    }

    private static PermissionGrant grant(PermissionKind kind, PermissionResource resource,
                                         PermissionGrantScope scope, PermissionPersistence persistence) {
        return new PermissionGrant(kind, resource, scope, persistence, Instant.now(), Optional.empty());
    }

    private static PermissionRequest request(PermissionRequestKind kind, PermissionResource resource,
                                              String reason, PermissionContext ctx) {
        return new PermissionRequest(UUID.randomUUID().toString(), kind, resource, reason,
                PermissionRequestDetails.of(kind.name(), reason), defaultChoices(), true,
                PermissionScope.ONCE, ctx);
    }

    private static List<PermissionChoice> defaultChoices() {
        return List.of(
                PermissionChoice.allowOnce("allow_once", "Allow once"),
                PermissionChoice.allowTurn("allow_turn", "Allow turn"),
                PermissionChoice.allowAlways("allow_always", "Allow always"),
                PermissionChoice.denyOnce("deny_once", "Deny once"),
                PermissionChoice.denyAlways("deny_always", "Deny always"),
                PermissionChoice.denyWithFeedback("deny_feedback", "Deny with feedback"));
    }

    private static PermissionChoice choiceFor(PermissionRequest request, PermissionPromptResult result) {
        List<PermissionChoice> matching = request.choices().stream()
                .filter(c -> c.decision() == result.decision()).toList();
        if (matching.size() == 1) return matching.getFirst();
        throw new IllegalArgumentException("Cannot resolve choice for decision " + result.decision());
    }

    private static boolean isAllow(PermissionDecision d) {
        return d == PermissionDecision.ALLOW_ONCE || d == PermissionDecision.ALLOW_TURN || d == PermissionDecision.ALLOW_ALWAYS;
    }

    private static PermissionGrantScope scopeFor(PermissionDecision d) {
        return switch (d) {
            case ALLOW_ONCE -> PermissionGrantScope.ONCE;
            case ALLOW_TURN -> PermissionGrantScope.TURN;
            case ALLOW_ALWAYS -> PermissionGrantScope.ALWAYS;
            default -> throw new IllegalArgumentException("deny cannot become grant");
        };
    }

    private static PermissionPersistence persistenceFor(PermissionDecision d) {
        return d == PermissionDecision.ALLOW_ALWAYS ? PermissionPersistence.USER : PermissionPersistence.MEMORY;
    }

    private static String requireText(String value, String name) {
        if (Objects.requireNonNull(value, name).isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
