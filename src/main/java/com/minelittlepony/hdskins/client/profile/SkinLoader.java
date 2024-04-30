package com.minelittlepony.hdskins.client.profile;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import com.google.common.cache.LoadingCache;
import com.minelittlepony.hdskins.HDSkinsServer;
import com.minelittlepony.hdskins.Memoize;
import com.minelittlepony.hdskins.client.HDSkins;
import com.minelittlepony.hdskins.client.SkinCacheClearCallback;
import com.minelittlepony.hdskins.profile.SkinType;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

public class SkinLoader {
    private final LoadingCache<GameProfile, CompletableFuture<ProvidedSkins>> cache = Memoize.createAsyncLoadingCache(15, profile -> {
        return CompletableFuture.supplyAsync(() -> HDSkinsServer.getInstance().getServers().fillProfile(profile), Util.getIoWorkerExecutor())
                .thenComposeAsync(this::fetchTextures, MinecraftClient.getInstance());
    });

    private final FileStore fileStore = new FileStore();

    public DynamicSkinTextures get(GameProfile profile) {
        return new DynamicSkinTextures() {
            private final AtomicReference<ProvidedSkins> value = new AtomicReference<>(load(profile).getNow(ProvidedSkins.EMPTY));

            @Override
            public Set<Identifier> getProvidedSkinTypes() {
                return value.get().getProvidedSkinTypes();
            }

            @Override
            public Optional<Identifier> getSkin(SkinType type) {
                return value.get().getSkin(type);
            }

            @Override
            public String getModel(String fallback) {
                return value.get().getModel(fallback);
            }

            @Override
            public boolean hasChanged() {
                final ProvidedSkins value = load(profile).getNow(ProvidedSkins.EMPTY);
                return this.value.getAndSet(value) != value;
            }
        };
    }

    public CompletableFuture<ProvidedSkins> load(GameProfile profile) {
        return cache.getUnchecked(profile);
    }

    private CompletableFuture<ProvidedSkins> fetchTextures(Map<SkinType, MinecraftProfileTexture> textures) {
        Map<SkinType, CompletableFuture<Identifier>> tasks = textures.entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> fileStore.get(entry.getKey(), entry.getValue())
        ));

        return CompletableFuture.allOf(tasks.values().stream().toArray(CompletableFuture[]::new)).thenApply(nothing -> {
            return new ProvidedSkins(
                    Optional.ofNullable(textures.get(SkinType.SKIN)).map(skin -> skin.getMetadata("model")),
                    tasks.keySet().stream().map(SkinType::getId).collect(Collectors.toSet()),
                    tasks.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> {
                        return entry.getValue().join();
                    }))
            );
        });
    }

    public void clear() {
        HDSkins.LOGGER.info("Clearing local player skin cache");
        cache.invalidateAll();
        HDSkinsServer.getInstance().getServers().invalidateProfiles();

        fileStore.clear();
        SkinCacheClearCallback.EVENT.invoker().onSkinCacheCleared();
    }

    public record ProvidedSkins (Optional<String> model, Set<Identifier> providedSkinTypes, Map<SkinType, Identifier> skins) implements DynamicSkinTextures {
        public static final ProvidedSkins EMPTY = new ProvidedSkins(Optional.empty(), Set.of(), Map.of());

        @Override
        public Set<Identifier> getProvidedSkinTypes() {
            return providedSkinTypes;
        }

        @Override
        public Optional<Identifier> getSkin(SkinType type) {
            return Optional.ofNullable(skins.get(type));
        }

        @Override
        public String getModel(String fallback) {
            return model.orElse(fallback);
        }

        @Override
        public boolean hasChanged() {
            return false;
        }
    }
}
