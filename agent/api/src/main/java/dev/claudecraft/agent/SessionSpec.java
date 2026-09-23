package dev.claudecraft.agent;

import dev.claudecraft.agent.tool.Tool;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;

public final class SessionSpec {
    private final Path workspace;
    private String resumeId;
    private String model;
    private String permissionMode;
    private String title;
    private String instructions;
    private String toolNamespace = "tools";
    private List<Tool> tools = Collections.emptyList();
    private Executor callbacks = Runnable::run;

    public SessionSpec(Path workspace) {
        this.workspace = workspace;
    }

    public SessionSpec resume(String sessionId) {
        this.resumeId = sessionId;
        return this;
    }

    public SessionSpec model(String model) {
        this.model = model;
        return this;
    }

    public SessionSpec permissionMode(String permissionMode) {
        this.permissionMode = permissionMode;
        return this;
    }

    public SessionSpec title(String title) {
        this.title = title;
        return this;
    }

    public SessionSpec instructions(String instructions) {
        this.instructions = instructions;
        return this;
    }

    public SessionSpec tools(String namespace, List<Tool> tools) {
        this.toolNamespace = namespace;
        this.tools = tools;
        return this;
    }

    public SessionSpec callbacks(Executor callbacks) {
        this.callbacks = callbacks;
        return this;
    }

    public Path workspace() {
        return workspace;
    }

    public String resumeId() {
        return resumeId;
    }

    public String model() {
        return model;
    }

    public String permissionMode() {
        return permissionMode;
    }

    public String title() {
        return title;
    }

    public String instructions() {
        return instructions;
    }

    public String toolNamespace() {
        return toolNamespace;
    }

    public List<Tool> tools() {
        return tools;
    }

    public Executor callbacks() {
        return callbacks;
    }
}
