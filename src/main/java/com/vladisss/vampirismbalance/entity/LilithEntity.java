package com.vladisss.vampirismbalance.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.HashSet;

public class LilithEntity extends TamableAnimal {

    private enum State { FOLLOW, WAIT_IN_NETHER, LAVA_SWIM }
    private State state = State.FOLLOW;

    // Lava swim
    private static final String TAG_SWIM_END_TICK = "vbl_lilith_swim_end";
    private static final String TAG_TARGET_X = "vbl_lilith_target_x";
    private static final String TAG_TARGET_Y = "vbl_lilith_target_y";
    private static final String TAG_TARGET_Z = "vbl_lilith_target_z";

    // Portal
    private static final String TAG_PORTAL_TARGET_X = "vbl_lilith_portal_x";
    private static final String TAG_PORTAL_TARGET_Y = "vbl_lilith_portal_y";
    private static final String TAG_PORTAL_TARGET_Z = "vbl_lilith_portal_z";

    // Lava-pool scan config
    private static final int LAVA_MIN_BLOCKS = 80;
    private static final int LAVA_SCAN_RADIUS = 12;
    private static final int LAVA_SCAN_LIMIT = 600;
    private static final long LAVA_SWIM_TICKS = 140L; // 7 seconds

    public LilithEntity(EntityType<? extends TamableAnimal> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 40.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.30D)
                .add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    @Override
    protected void registerGoals() {
        // ВАЖНО: не добавляем FloatGoal/FollowOwnerGoal,
        // чтобы они не ломали ручную логику в лаве.
        // Follow делаем вручную в tick().
    }

    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob mate) {
        return null;
    }

    @Override
    public boolean removeWhenFarAway(double dist) {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) return;

        // 1) Лавовый режим — всегда приоритетнее
        if (state == State.LAVA_SWIM) {
            this.getNavigation().stop();
            tickLavaSwim();
            return;
        }

        // 2) Ожидание в аду
        if (state == State.WAIT_IN_NETHER) {
            waitInNetherUntilOwner();
            return;
        }

        // 3) Обычный режим: следование + портал
        if (state == State.FOLLOW) {
            tryEnterPortalIfNearby();

            // простое “держаться рядом”
            LivingEntity owner = this.getOwner();
            if (owner != null) {
                double d2 = this.distanceToSqr(owner);
                if (d2 > (2.0 * 2.0)) {
                    this.getNavigation().moveTo(owner, 1.15D);
                } else {
                    this.getNavigation().stop();
                }
            }

            // ВАЖНО: запускаем ритуал не только "в лаве",
            // но и если стоит прямо над лавой.
            if (isOnOrOverLava()) {
                tryStartLavaSwim();
            }
        }
    }

    private boolean isOnOrOverLava() {
        BlockPos p = this.blockPosition();
        return this.level().getFluidState(p).is(FluidTags.LAVA)
                || this.level().getFluidState(p.below()).is(FluidTags.LAVA);
    }

    // ---------------- LAVA SWIM ----------------

    private void tryStartLavaSwim() {
        if (state == State.LAVA_SWIM) return;
        if (!(this.level() instanceof ServerLevel level)) return;

        BlockPos start = this.blockPosition();

        // если лавы нет в ногах и под ногами — не стартуем
        if (!level.getFluidState(start).is(FluidTags.LAVA) && !level.getFluidState(start.below()).is(FluidTags.LAVA)) {
            return;
        }
        if (!level.getFluidState(start).is(FluidTags.LAVA)) start = start.below();

        LavaScan scan = scanLavaPool(level, start, LAVA_SCAN_RADIUS, LAVA_SCAN_LIMIT);
        if (scan.count < LAVA_MIN_BLOCKS) return;

        Vec3 target = scan.pickCenterTargetNearSurface(level);

        state = State.LAVA_SWIM;

        long now = this.level().getGameTime();
        CompoundTag tag = this.getPersistentData();
        tag.putLong(TAG_SWIM_END_TICK, now + LAVA_SWIM_TICKS);
        tag.putDouble(TAG_TARGET_X, target.x);
        tag.putDouble(TAG_TARGET_Y, target.y);
        tag.putDouble(TAG_TARGET_Z, target.z);

        this.getNavigation().stop();
        this.setDeltaMovement(Vec3.ZERO);
        this.hurtMarked = true;
    }

    private void tickLavaSwim() {
        if (!(this.level() instanceof ServerLevel level)) return;

        CompoundTag tag = this.getPersistentData();
        long end = tag.getLong(TAG_SWIM_END_TICK);

        Vec3 target = new Vec3(tag.getDouble(TAG_TARGET_X), tag.getDouble(TAG_TARGET_Y), tag.getDouble(TAG_TARGET_Z));
        Vec3 to = target.subtract(this.position());
        Vec3 horiz = new Vec3(to.x, 0, to.z);
        double dist = horiz.length();

        // чуть притапливаем, чтобы не было "ровно парит"
        double ySink = -0.02;

        if (dist > 0.10) {
            Vec3 dir = horiz.normalize();
            double speed = 0.18;
            this.setDeltaMovement(dir.x * speed, ySink, dir.z * speed);
            this.hurtMarked = true;
        } else {
            this.setDeltaMovement(0, ySink, 0);
        }

        long now = this.level().getGameTime();
        if (now >= end) {
            this.teleportTo(target.x, target.y, target.z);

            WitherBoss wither = EntityType.WITHER.create(level);
            if (wither != null) {
                wither.moveTo(target.x, target.y + 1.0, target.z, level.random.nextFloat() * 360F, 0);
                wither.setPersistenceRequired();
                level.addFreshEntity(wither);
            }

            this.discard();
        }
    }

    private static class LavaScan {
        final int count;
        final double avgX;
        final double avgZ;

        LavaScan(int count, double avgX, double avgZ) {
            this.count = count;
            this.avgX = avgX;
            this.avgZ = avgZ;
        }

        Vec3 pickCenterTargetNearSurface(ServerLevel level) {
            int x = (int) Math.round(avgX);
            int z = (int) Math.round(avgZ);

            int y0 = level.getMinBuildHeight();
            int y1 = level.getMaxBuildHeight() - 1;

            for (int y = y1; y >= y0; y--) {
                BlockPos p = new BlockPos(x, y, z);
                if (level.getFluidState(p).is(FluidTags.LAVA)) {
                    return new Vec3(x + 0.5, y + 0.1, z + 0.5);
                }
            }
            return new Vec3(x + 0.5, level.getSeaLevel(), z + 0.5);
        }
    }

    private static LavaScan scanLavaPool(ServerLevel level, BlockPos start, int radius, int limit) {
        ArrayDeque<BlockPos> q = new ArrayDeque<>();
        HashSet<Long> seen = new HashSet<>();

        q.add(start);
        seen.add(start.asLong());

        int count = 0;
        long sumX = 0;
        long sumZ = 0;

        while (!q.isEmpty() && count < limit) {
            BlockPos p = q.poll();

            if (Math.abs(p.getX() - start.getX()) > radius) continue;
            if (Math.abs(p.getY() - start.getY()) > 4) continue;
            if (Math.abs(p.getZ() - start.getZ()) > radius) continue;

            if (!level.getFluidState(p).is(FluidTags.LAVA)) continue;

            count++;
            sumX += p.getX();
            sumZ += p.getZ();

            for (BlockPos n : new BlockPos[]{ p.north(), p.south(), p.west(), p.east(), p.above(), p.below() }) {
                long key = n.asLong();
                if (seen.add(key)) q.add(n);
            }
        }

        if (count == 0) return new LavaScan(0, start.getX(), start.getZ());
        return new LavaScan(count, (double) sumX / count, (double) sumZ / count);
    }

    // ---------------- PORTAL LOGIC ----------------

    private void tryEnterPortalIfNearby() {
        if (!(this.level() instanceof ServerLevel level)) return;
        if (level.dimension() != Level.OVERWORLD) return;

        LivingEntity owner = this.getOwner();
        if (!(owner instanceof Player)) return;
        if (this.distanceToSqr(owner) > (12.0 * 12.0)) return;

        BlockPos portal = findNearestPortal(level, this.blockPosition(), 4);
        if (portal == null) return;

        this.getNavigation().moveTo(portal.getX() + 0.5, portal.getY(), portal.getZ() + 0.5, 1.2);

        if (level.getBlockState(this.blockPosition()).is(Blocks.NETHER_PORTAL)) {
            ServerLevel nether = level.getServer().getLevel(Level.NETHER);
            if (nether == null) return;

            CompoundTag t = this.getPersistentData();
            t.putInt(TAG_PORTAL_TARGET_X, portal.getX());
            t.putInt(TAG_PORTAL_TARGET_Y, portal.getY());
            t.putInt(TAG_PORTAL_TARGET_Z, portal.getZ());

            Entity moved = this.changeDimension(nether);
            if (moved instanceof LilithEntity lilith) {
                lilith.state = State.WAIT_IN_NETHER;
                lilith.getNavigation().stop();
            }
        }
    }

    private BlockPos findNearestPortal(ServerLevel level, BlockPos center, int r) {
        BlockPos best = null;
        double bestD2 = Double.MAX_VALUE;

        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    BlockPos p = center.offset(dx, dy, dz);
                    if (!level.getBlockState(p).is(Blocks.NETHER_PORTAL)) continue;
                    double d2 = p.distSqr(center);
                    if (d2 < bestD2) {
                        bestD2 = d2;
                        best = p.immutable();
                    }
                }
            }
        }
        return best;
    }

    private void waitInNetherUntilOwner() {
        if (!(this.level() instanceof ServerLevel level)) return;

        if (level.dimension() != Level.NETHER) {
            state = State.FOLLOW;
            return;
        }

        LivingEntity owner = this.getOwner();
        if (owner != null && owner.level().dimension() == Level.NETHER) {
            state = State.FOLLOW;
            return;
        }

        this.getNavigation().stop();
        this.setDeltaMovement(Vec3.ZERO);
    }
}
