package com.fastasyncworldedit.fabric;

import com.fastasyncworldedit.core.FAWEPlatformAdapterImpl;
import com.fastasyncworldedit.core.Fawe;
import com.fastasyncworldedit.core.IFawe;
import com.fastasyncworldedit.core.queue.implementation.QueueHandler;
import com.fastasyncworldedit.core.queue.implementation.preloader.AsyncPreloader;
import com.fastasyncworldedit.core.queue.implementation.preloader.Preloader;
import com.fastasyncworldedit.core.regions.FaweMaskManager;
import com.fastasyncworldedit.core.util.TaskManager;
import com.sk89q.worldedit.fabric.adapter.v26_2.FabricAdapterBootstrap;
import com.sk89q.worldedit.fabric.internal.FabricWorldEdit;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.PlayerList;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.UUID;

/**
 * Fabric implementation of {@link IFawe}.
 */
public class FaweFabric implements IFawe {

    private final FabricWorldEdit mod;
    private final File directory;
    private final FAWEPlatformAdapterImpl platformAdapter;
    private Preloader preloader;

    public FaweFabric(FabricWorldEdit mod) {
        this.mod = mod;
        this.directory = FabricLoader.getInstance().getConfigDir().resolve("worldedit").toFile();
        this.platformAdapter = FabricAdapterBootstrap.getPlatformAdapter();
        try {
            Fawe.set(this);
            Fawe.setupInjector();
        } catch (Throwable t) {
            throw new IllegalStateException("Failed to initialize FAWE on Fabric", t);
        }
    }

    @Override
    public File getDirectory() {
        return directory;
    }

    @Override
    public TaskManager getTaskManager() {
        return new FabricTaskManager();
    }

    @Override
    public Collection<FaweMaskManager> getMaskManagers() {
        return Collections.emptyList();
    }

    @Override
    public String getPlatform() {
        return "Fabric";
    }

    @Override
    public UUID getUUID(String name) {
        MinecraftServer server = FabricTaskManager.serverOrNull();
        if (server == null) {
            return null;
        }
        PlayerList list = server.getPlayerList();
        var player = list.getPlayerByName(name);
        return player != null ? player.getUUID() : null;
    }

    @Override
    public String getName(UUID uuid) {
        MinecraftServer server = FabricTaskManager.serverOrNull();
        if (server == null) {
            return null;
        }
        var player = server.getPlayerList().getPlayer(uuid);
        return player != null ? player.getGameProfile().name() : null;
    }

    @Override
    public String getDebugInfo() {
        return "# FastAsyncWorldEdit Fabric\n"
                + Fawe.instance().getVersion() + "\n"
                + "Minecraft: 26.2\n"
                + "Loader: " + FabricLoader.getInstance().getModContainer("fabricloader")
                .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("unknown") + "\n";
    }

    @Override
    public QueueHandler getQueueHandler() {
        return new FabricQueueHandler();
    }

    @Override
    public Preloader getPreloader(boolean initialise) {
        if (preloader == null && initialise) {
            preloader = new AsyncPreloader();
        }
        return preloader;
    }

    @Override
    public FAWEPlatformAdapterImpl getPlatformAdapter() {
        return platformAdapter;
    }

    public FabricWorldEdit getMod() {
        return mod;
    }
}
