package dev.oreo.modulith.example.economy;

import dev.oreo.modulith.core.MinecraftModule;
import dev.oreo.modulith.core.ModuleContext;
import dev.oreo.modulith.core.PluginModule;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@PluginModule(value = "economy", configuration = true)
public final class EconomyModule implements MinecraftModule, EconomyService {
    private final Map<UUID, Long> balances = new ConcurrentHashMap<>();

    @Override
    public void enable(ModuleContext context) {
        context.services().publish(EconomyService.class, this);

        if (context.config().get("starting-balance") == null) {
            context.config().set("starting-balance", 100L);
            context.config().save();
        }
    }

    @Override
    public long balance(UUID playerId) {
        return balances.computeIfAbsent(
                playerId,
                ignored -> 100L
        );
    }

    @Override
    public void deposit(UUID playerId, long amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("amount must be >= 0");
        }
        balances.merge(playerId, amount, Long::sum);
    }
}
