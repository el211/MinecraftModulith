package dev.oreo.modulith.paper;

import dev.oreo.modulith.core.ModuleContext;
import dev.oreo.modulith.core.ModulithException;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Objects;

/** Paper resources automatically tied to a module lifecycle. */
public final class PaperPlatform {
    private final JavaPlugin plugin;
    private final SchedulerAdapter scheduler;

    PaperPlatform(JavaPlugin plugin) {
        this.plugin = plugin;
        this.scheduler = SchedulerAdapter.detect(plugin);
    }

    public JavaPlugin plugin() {
        return plugin;
    }

    public SchedulerAdapter scheduler() {
        return scheduler;
    }

    public <T extends Listener> T registerListener(ModuleContext context, T listener) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(listener, "listener");
        Bukkit.getPluginManager().registerEvents(listener, plugin);
        context.lifecycle().onClose(() -> HandlerList.unregisterAll(listener));
        return listener;
    }

    /** Scheduler-safe API that works on Paper and Folia global scheduling. */
    public ScheduledTaskHandle schedule(ModuleContext context, Runnable runnable) {
        ScheduledTaskHandle task = scheduler.run(runnable);
        context.lifecycle().onClose(task::cancel);
        return task;
    }

    public ScheduledTaskHandle scheduleLater(ModuleContext context, Runnable runnable, long delayTicks) {
        ScheduledTaskHandle task = scheduler.runLater(runnable, delayTicks);
        context.lifecycle().onClose(task::cancel);
        return task;
    }

    public ScheduledTaskHandle scheduleTimer(
            ModuleContext context,
            Runnable runnable,
            long delayTicks,
            long periodTicks
    ) {
        ScheduledTaskHandle task = scheduler.runTimer(runnable, delayTicks, periodTicks);
        context.lifecycle().onClose(task::cancel);
        return task;
    }

    /**
     * Standard Paper-only scheduler compatibility method. Prefer {@link #schedule(ModuleContext, Runnable)}
     * when Folia compatibility is required.
     */
    public BukkitTask runTask(ModuleContext context, Runnable runnable) {
        ensurePaperScheduler();
        BukkitTask task = Bukkit.getScheduler().runTask(plugin, runnable);
        context.lifecycle().onClose(task::cancel);
        return task;
    }

    public BukkitTask runTaskLater(ModuleContext context, Runnable runnable, long delayTicks) {
        ensurePaperScheduler();
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, runnable, delayTicks);
        context.lifecycle().onClose(task::cancel);
        return task;
    }

    public BukkitTask runTaskTimer(ModuleContext context, Runnable runnable, long delayTicks, long periodTicks) {
        ensurePaperScheduler();
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, runnable, delayTicks, periodTicks);
        context.lifecycle().onClose(task::cancel);
        return task;
    }

    public CommandRegistration registerCommand(
            ModuleContext context,
            String name,
