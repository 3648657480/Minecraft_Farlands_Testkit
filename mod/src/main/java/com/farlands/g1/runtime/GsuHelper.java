package com.farlands.g1.runtime;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.systems.RenderSystem;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GlobalSettingsUniform;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.system.MemoryStack;

/**
 * Floating-origin UBO upload, invoked from the patched
 * {@code GlobalSettingsUniform.update(...)}.
 *
 * <p>The origin is snapped to a multiple of 16 blocks so the fractional part
 * of the camera position stays small enough to keep float precision at
 * coordinates up to 2^63. The integer part keeps the true world position.</p>
 */
public final class GsuHelper {

    private GsuHelper() {
    }

    private static final Field BUFFER;
    private static final Field ORIGIN;

    static {
        try {
            BUFFER = GlobalSettingsUniform.class.getDeclaredField("buffer");
            BUFFER.setAccessible(true);
            ORIGIN = GlobalSettingsUniform.class.getDeclaredField("renderOrigin");
            ORIGIN.setAccessible(true);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("FarLands patch not applied to GlobalSettingsUniform", e);
        }
    }

    public static void update(GlobalSettingsUniform gsu, int width, int height, double glintAlpha,
                              long gameTime, DeltaTracker deltaTracker, int menuBlurRadius,
                              Vec3 cameraPos, boolean useRgss) {
        double ox = Math.floor((cameraPos.x + 8.0) / 16.0) * 16.0;
        double oy = Math.floor((cameraPos.y + 8.0) / 16.0) * 16.0;
        double oz = Math.floor((cameraPos.z + 8.0) / 16.0) * 16.0;
        setOrigin(new Vec3(ox, oy, oz));
        double cx = cameraPos.x - ox;
        double cy = cameraPos.y - oy;
        double cz = cameraPos.z - oz;

        MemoryStack stack = MemoryStack.stackPush();
        try {
            int worldCamX = (int) (long) Math.floor(cameraPos.x);
            int worldCamY = (int) (long) Math.floor(cameraPos.y);
            int worldCamZ = (int) (long) Math.floor(cameraPos.z);
            int flatCamX = Mth.floor(cx);
            int flatCamY = Mth.floor(cy);
            int flatCamZ = Mth.floor(cz);
            ByteBuffer data = Std140Builder.onStack(stack, GlobalSettingsUniform.UBO_SIZE)
                .putIVec3(worldCamX, worldCamY, worldCamZ)
                .putVec3((float) ((double) flatCamX - cx), (float) ((double) flatCamY - cy), (float) ((double) flatCamZ - cz))
                .putVec2((float) width, (float) height)
                .putFloat((float) glintAlpha)
                .putFloat(((float) (gameTime % 24000L) + deltaTracker.getGameTimeDeltaPartialTick(false)) / 24000.0F)
                .putInt(menuBlurRadius)
                .putInt(useRgss ? 1 : 0)
                .get();
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer(gsu).slice(), data);
        } finally {
            stack.close();
        }
        RenderSystem.setGlobalSettingsUniform(buffer(gsu));
    }

    private static GpuBuffer buffer(GlobalSettingsUniform gsu) {
        try {
            return (GpuBuffer) BUFFER.get(gsu);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void setOrigin(Vec3 origin) {
        try {
            ORIGIN.set(null, origin);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }
}
