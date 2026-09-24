package dev.oreo.modulith.paper;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Objects;

final class PaperSchedulerAdapter implements SchedulerAdapter {
    private final JavaPlugin plugin;

    PaperSchedulerAdapter(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @Override
    public ScheduledTaskHandle run(Runnable runnable) {
        BukkitTask task = Bukkit.getScheduler().runTask(plugin, runnable);
        return task::cancel;
    }

    @Override
    public ScheduledTaskHandle runLater(Runnable runnable, long delayTicks) {
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, runnable, delayTicks);
        return task::cancel;
    }

    @Override
    public ScheduledTaskHandle runTimer(Runnable runnable, long delayTicks, long periodTicks) {
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, runnable, delayTicks, periodTicks);
        return task::cancel;
    }

    @Override
    public boolean folia() {
        return false;
    }
}
