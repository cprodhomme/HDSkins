package com.minelittlepony.hdskins.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.minelittlepony.hdskins.HDSkinsServer;
import com.mojang.authlib.GameProfile;

import net.minecraft.server.network.ServerLoginNetworkHandler;

@Mixin(ServerLoginNetworkHandler.class)
abstract class MixinServerLoginNetworkHandler {
    @Inject(method = "startVerify", at = @At("HEAD"))
    private void onStartVerify(GameProfile profile, CallbackInfo info) {
        HDSkinsServer.getInstance().getServers().fillProfile(profile);
    }
}
