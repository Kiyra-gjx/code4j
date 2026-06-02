package code4j.permissions.model;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

public sealed interface PermissionResource
        permits PermissionResource.PathResource, PermissionResource.CommandResource,
        PermissionResource.EditResource, PermissionResource.McpToolResource {

    record PathResource(Path path, PathIntent intent) implements PermissionResource {
        public PathResource {
            path = Objects.requireNonNull(path, "path");
            intent = Objects.requireNonNull(intent, "intent");
        }
    }

    record CommandResource(CommandSignature signature, CommandClassification classification)
            implements PermissionResource {
        public CommandResource {
            signature = Objects.requireNonNull(signature, "signature");
            classification = Objects.requireNonNull(classification, "classification");
        }
    }

    record EditResource(Path path, String summary, String diffPreview) implements PermissionResource {
        public EditResource {
            path = Objects.requireNonNull(path, "path");
            summary = Objects.requireNonNull(summary, "summary");
            diffPreview = Objects.requireNonNull(diffPreview, "diffPreview");
        }
    }

    record McpToolResource(String serverName, String toolName, String wrappedName, String description)
            implements PermissionResource {
        public McpToolResource {
            serverName = requireText(serverName, "serverName");
            toolName = requireText(toolName, "toolName");
            wrappedName = requireText(wrappedName, "wrappedName");
            description = Objects.requireNonNull(description, "description");
        }
    }

    private static String requireText(String value, String name) {
        if (Objects.requireNonNull(value, name).isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
