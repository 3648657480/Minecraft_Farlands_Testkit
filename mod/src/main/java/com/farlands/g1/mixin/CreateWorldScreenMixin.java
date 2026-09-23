package com.farlands.g1.mixin;

import com.farlands.g1.client.FarLandsTerrainTab;
import com.farlands.g1.client.FarLandsTestTab;
import com.farlands.g1.client.FarLandsWorldTab;
import com.farlands.g1.client.WorldDraft;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.tabs.MenuTabBar;
import net.minecraft.client.gui.components.tabs.Tab;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Adds the FarLands tabs to the create-world screen and writes the chosen
 * configuration into the new world folder before generation.
 *
 * <p>This is the whole point of the config design: the properties are set
 * BEFORE world creation, and never edited from inside a running world.</p>
 */
@Mixin(CreateWorldScreen.class)
public class CreateWorldScreenMixin {

    @Redirect(method = "init",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/tabs/MenuTabBar$Builder;addTabs([Lnet/minecraft/client/gui/components/tabs/Tab;)Lnet/minecraft/client/gui/components/tabs/MenuTabBar$Builder;"))
    private MenuTabBar.Builder farlands$addConfigTabs(MenuTabBar.Builder builder, Tab[] tabs) {
        return builder.addTabs(tabs)
            .addTab(new FarLandsWorldTab())
            .addTab(new FarLandsTerrainTab())
            .addTab(new FarLandsTestTab());
    }

    @Inject(method = "createNewWorldDirectory", at = @At("RETURN"))
    private static void farlands$seedNewWorld(Minecraft minecraft, String folder, Path tempDataPackDir,
            CallbackInfoReturnable<Optional<LevelStorageSource.LevelStorageAccess>> cir) {
        try {
            Optional<LevelStorageSource.LevelStorageAccess> access = cir.getReturnValue();
            if (access != null && access.isPresent()) {
                WorldDraft.writeTo(access.get().getLevelDirectory().path());
            }
        } catch (Throwable t) {
            System.out.println("[FarLands-G1] seed world config FAILED: " + t);
        }
    }
}
