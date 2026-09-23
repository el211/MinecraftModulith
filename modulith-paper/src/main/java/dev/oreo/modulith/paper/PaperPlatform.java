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
            String description,
            CommandExecutor executor
    ) {
        return registerCommand(context, name, description, executor, null);
    }

    public CommandRegistration registerCommand(
            ModuleContext context,
            String name,
            String description,
            CommandExecutor executor,
            TabCompleter tabCompleter
    ) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(executor, "executor");

        try {
            Constructor<PluginCommand> constructor = PluginCommand.class
                    .getDeclaredConstructor(String.class, Plugin.class);
            constructor.setAccessible(true);
            PluginCommand command = constructor.newInstance(name, plugin);
            command.setDescription(description == null ? "" : description);
            command.setExecutor(executor);
            if (tabCompleter != null) {
                command.setTabCompleter(tabCompleter);
            }

            Object commandMap = resolveCommandMap();
            Method register = findMethod(commandMap.getClass(), "register", 2);
            register.invoke(commandMap, plugin.getName().toLowerCase(), command);

            CommandRegistration registration = new CommandRegistration(
                    command,
                    () -> unregisterCommand(commandMap, command)
            );
            context.lifecycle().onClose(registration::close);
            return registration;
        } catch (ReflectiveOperationException exception) {
            throw new ModulithException("Could not dynamically register command '/" + name + "'", exception);
        }
    }

    private Object resolveCommandMap() throws ReflectiveOperationException {
        Method method = findMethod(plugin.getServer().getClass(), "getCommandMap", 0);
        return method.invoke(plugin.getServer());
    }

    @SuppressWarnings("unchecked")
    private void unregisterCommand(Object commandMap, PluginCommand command) {
        try {
            Method unregister = findMethod(command.getClass(), "unregister", 1);
            unregister.invoke(command, commandMap);

            Field knownCommands = findField(commandMap.getClass(), "knownCommands");
            knownCommands.setAccessible(true);
            Object value = knownCommands.get(commandMap);
            if (value instanceof Map<?, ?> map) {
                ((Map<String, Command>) map).entrySet().removeIf(entry -> entry.getValue() == command);
            }
        } catch (ReflectiveOperationException exception) {
            throw new ModulithException("Could not unregister command '/" + command.getName() + "'", exception);
        }
    }

    private static Method findMethod(Class<?> type, String name, int parameterCount) {
        Class<?> current = type;
        while (current != null) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.getName().equals(name) && method.getParameterCount() == parameterCount) {
                    method.setAccessible(true);
                    return method;
                }
            }
            current = current.getSuperclass();
        }
        throw new ModulithException("Could not find method " + name + " on " + type.getName());
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private void ensurePaperScheduler() {
        if (scheduler.folia()) {
            throw new ModulithException(
                    "BukkitScheduler compatibility methods are unsafe on Folia; use schedule/scheduleLater/scheduleTimer"
            );
        }
    }
}
