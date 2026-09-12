package dev.oreo.modulith.example;

import dev.oreo.modulith.paper.PaperModulith;
import dev.oreo.modulith.sqlite.SqliteEventPublicationRegistry;
import org.bukkit.plugin.java.JavaPlugin;

public final class ExamplePlugin extends JavaPlugin {
    private PaperModulith modulith;
    private SqliteEventPublicationRegistry publicationRegistry;

    @Override
    public void onEnable() {
        publicationRegistry = new SqliteEventPublicationRegistry(
                getDataFolder().toPath().resolve("modulith-events.db")
        );

        modulith = PaperModulith.builder(this)
                .basePackage("dev.oreo.modulith.example")
                .publicationRegistry(publicationRegistry)
                .start();

        getLogger().info("Module graph:\n" + modulith.runtime().graphMermaid());
        getLogger().info("Diagnostics: " + modulith.runtime().diagnostics());
    }

    @Override
    public void onDisable() {
        if (modulith != null) {
            modulith.close();
        }
        if (publicationRegistry != null) {
            publicationRegistry.close();
        }
    }
}
