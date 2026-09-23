package dev.oreo.modulith.paper;

import org.bukkit.command.PluginCommand;

import java.util.Objects;

/** A dynamically registered command tied to a module lifecycle. */
public final class CommandRegistration implements AutoCloseable {
    private final PluginCommand command;
    private final Runnable unregister;
    private boolean closed;

    CommandRegistration(PluginCommand command, Runnable unregister) {
        this.command = Objects.requireNonNull(command, "command");
        this.unregister = Objects.requireNonNull(unregister, "unregister");
    }

    public PluginCommand command() {
        return command;
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        unregister.run();
        closed = true;
    }
}
