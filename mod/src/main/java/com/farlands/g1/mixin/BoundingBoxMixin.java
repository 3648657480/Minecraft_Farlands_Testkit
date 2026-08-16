package com.farlands.g1.mixin;

import java.util.stream.IntStream;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BoundingBox.class)
public class BoundingBoxMixin {
   @Shadow private int minX;
   @Shadow private int minY;
   @Shadow private int minZ;
   @Shadow private int maxX;
   @Shadow private int maxY;
   @Shadow private int maxZ;

   @Unique
   private static long ul(int v) { return Integer.toUnsignedLong(v); }

   @Unique
   private static int clampCoord(long v) { return (int)Mth.clamp(v, -2147467264L, 2147467263L); }

   @Unique
   private static long ulMin(long a, long b) { return a < b ? a : b; }

   @Unique
   private static long ulMax(long a, long b) { return a > b ? a : b; }

    @Inject(method = "<init>(IIIIII)V", at = @At("TAIL"))
    private void farlands$fixInversion(int mx, int my, int mz, int Mx, int My, int Mz, CallbackInfo ci) {
       if (this.maxX < this.minX) {
         if (this.minX < Integer.MIN_VALUE + 10_000_000 && this.maxX < Integer.MIN_VALUE + 10_000_000) {
            if (ul(this.maxX) > ul(this.minX)) return;
         }
         int t = this.maxX; this.maxX = this.minX; this.minX = t;
      }
      if (this.maxZ < this.minZ) {
         if (this.minZ < Integer.MIN_VALUE + 10_000_000 && this.maxZ < Integer.MIN_VALUE + 10_000_000) {
            if (ul(this.maxZ) > ul(this.minZ)) return;
         }
         int t = this.maxZ; this.maxZ = this.minZ; this.minZ = t;
      }
   }

   @Overwrite
   public boolean intersects(BoundingBox other) {
      return ul(this.maxX) >= ul(other.minX()) && ul(this.minX) <= ul(other.maxX()) && ul(this.maxZ) >= ul(other.minZ()) && ul(this.minZ) <= ul(other.maxZ()) && ul(this.maxY) >= ul(other.minY()) && ul(this.minY) <= ul(other.maxY());
   }

   @Overwrite
   public boolean intersects(int minX, int minZ, int maxX, int maxZ) {
      return ul(this.maxX) >= ul(minX) && ul(this.minX) <= ul(maxX) && ul(this.maxZ) >= ul(minZ) && ul(this.minZ) <= ul(maxZ);
   }

   @Overwrite
   public boolean isInside(int x, int y, int z) {
      return ul(x) >= ul(this.minX) && ul(x) <= ul(this.maxX) && ul(z) >= ul(this.minZ) && ul(z) <= ul(this.maxZ) && y >= this.minY && y <= this.maxY;
   }

   @Overwrite
   public boolean isInside(Vec3i pos) { return this.isInside(pos.getX(), pos.getY(), pos.getZ()); }

   @Overwrite
   public Vec3i getLength() {
      return new Vec3i((int)(ul(this.maxX) - ul(this.minX)), (int)(ul(this.maxY) - ul(this.minY)), (int)(ul(this.maxZ) - ul(this.minZ)));
   }

   @Overwrite
   public int getXSpan() { return Math.clamp((long)((int)(ul(this.maxX) - ul(this.minX) + 1L)), 1, 256); }

   @Overwrite
   public int getYSpan() { return Math.clamp((long)((int)(ul(this.maxY) - ul(this.minY) + 1L)), 1, 256); }

   @Overwrite
   public int getZSpan() { return Math.clamp((long)((int)(ul(this.maxZ) - ul(this.minZ) + 1L)), 1, 256); }

   @Overwrite
   public BlockPos getCenter() {
      return new BlockPos(clampCoord(ul(this.minX) + (ul(this.maxX) - ul(this.minX) + 1L) / 2L), Mth.clamp(this.minY + (this.maxY - this.minY + 1) / 2, -64, 320), clampCoord(ul(this.minZ) + (ul(this.maxZ) - ul(this.minZ) + 1L) / 2L));
   }

   @Overwrite
   public BoundingBox encapsulate(BoundingBox other) {
      this.minX = (int)ulMin(ul(this.minX), ul(other.minX())); this.minY = Math.min(this.minY, other.minY()); this.minZ = (int)ulMin(ul(this.minZ), ul(other.minZ()));
      this.maxX = (int)ulMax(ul(this.maxX), ul(other.maxX())); this.maxY = Math.max(this.maxY, other.maxY()); this.maxZ = (int)ulMax(ul(this.maxZ), ul(other.maxZ()));
      return (BoundingBox)(Object)this;
   }

   @Overwrite
   public BoundingBox encapsulate(BlockPos pos) {
      this.minX = (int)ulMin(ul(this.minX), ul(pos.getX())); this.minY = Math.min(this.minY, pos.getY()); this.minZ = (int)ulMin(ul(this.minZ), ul(pos.getZ()));
      this.maxX = (int)ulMax(ul(this.maxX), ul(pos.getX())); this.maxY = Math.max(this.maxY, pos.getY()); this.maxZ = (int)ulMax(ul(this.maxZ), ul(pos.getZ()));
      return (BoundingBox)(Object)this;
   }

   @Overwrite
   public BoundingBox move(int dx, int dy, int dz) {
      this.minX = (int)(ul(this.minX) + dx); this.minY += dy; this.minZ = (int)(ul(this.minZ) + dz);
      this.maxX = (int)(ul(this.maxX) + dx); this.maxY += dy; this.maxZ = (int)(ul(this.maxZ) + dz);
      return (BoundingBox)(Object)this;
   }

   @Overwrite
   public BoundingBox move(Vec3i offset) { return this.move(offset.getX(), offset.getY(), offset.getZ()); }

   @Overwrite
   public BoundingBox moved(int dx, int dy, int dz) {
      return new BoundingBox((int)(ul(this.minX) + dx), this.minY + dy, (int)(ul(this.minZ) + dz), (int)(ul(this.maxX) + dx), this.maxY + dy, (int)(ul(this.maxZ) + dz));
   }

   @Overwrite
   public BoundingBox inflatedBy(int x, int y, int z) {
      return new BoundingBox((int)(ul(this.minX) - x), this.minY - y, (int)(ul(this.minZ) - z), (int)(ul(this.maxX) + x), this.maxY + y, (int)(ul(this.maxZ) + z));
   }

   @Overwrite
   public BoundingBox inflatedBy(int amount) { return this.inflatedBy(amount, amount, amount); }

   @Overwrite
   public static BoundingBox fromCorners(Vec3i a, Vec3i b) {
      return new BoundingBox((int)ulMin(ul(a.getX()), ul(b.getX())), Math.min(a.getY(), b.getY()), (int)ulMin(ul(a.getZ()), ul(b.getZ())), (int)ulMax(ul(a.getX()), ul(b.getX())), Math.max(a.getY(), b.getY()), (int)ulMax(ul(a.getZ()), ul(b.getZ())));
   }

   @Overwrite
   public static BoundingBox encapsulating(BoundingBox a, BoundingBox b) {
      return new BoundingBox((int)ulMin(ul(a.minX()), ul(b.minX())), Math.min(a.minY(), b.minY()), (int)ulMin(ul(a.minZ()), ul(b.minZ())), (int)ulMax(ul(a.maxX()), ul(b.maxX())), Math.max(a.maxY(), b.maxY()), (int)ulMax(ul(a.maxZ()), ul(b.maxZ())));
   }

    @Overwrite
    public Stream<ChunkPos> intersectingChunks() {
        int bx1 = (int)(ul(this.minX) >> 4), bx2 = (int)(ul(this.maxX) >> 4);
        int bz1 = (int)(ul(this.minZ) >> 4), bz2 = (int)(ul(this.maxZ) >> 4);
        int x1 = Math.min(bx1, bx2), x2 = Math.max(bx1, bx2);
        int z1 = Math.min(bz1, bz2), z2 = Math.max(bz1, bz2);
        if ((long)x2 - x1 > 1000L || (long)z2 - z1 > 1000L) return Stream.empty();
        return IntStream.rangeClosed(x1, x2).boxed().flatMap(x -> IntStream.rangeClosed(z1, z2).mapToObj(z -> new ChunkPos(x, z)));
    }

   @Overwrite
   public String toString() {
      return "BoundingBox{minX=" + this.minX + ", minY=" + this.minY + ", minZ=" + this.minZ + ", maxX=" + this.maxX + ", maxY=" + this.maxY + ", maxZ=" + this.maxZ + "}";
   }
}

