package com.jamesrenrold.deathbecomes.entity;

import com.jamesrenrold.deathbecomes.DeathBecomes;
import com.jamesrenrold.deathbecomes.item.ModItems;
import com.jamesrenrold.deathbecomes.network.DeathBecomesNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Vector3f;

import java.util.UUID;

public class HunterEntity extends Monster {
    private static final EntityDataAccessor<Integer> TARGET_ID = SynchedEntityData.defineId(HunterEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> PHASE = SynchedEntityData.defineId(HunterEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> SHAKE = SynchedEntityData.defineId(HunterEntity.class, EntityDataSerializers.BOOLEAN);
    private static final byte INTRO = 0, CHASE = 1, CAPTURE = 2, DEPART = 3, ESCAPE = 4, WALK_AWAY = 5;
    private UUID targetUuid;
    private int phaseTicks;
    private int captureTicks;
    private int shakeTicks;
    private float damageTaken;
    private long captureStartedAt;
    private boolean rewardGranted;
    private Vec3 departureDirection = new Vec3(0.0D, 0.0D, 1.0D);

    public HunterEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 10000.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.20D)
                .add(Attributes.ATTACK_DAMAGE, 0.0D)
                .add(Attributes.FOLLOW_RANGE, 128.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.3D, true));
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(TARGET_ID, -1);
        entityData.define(PHASE, INTRO);
        entityData.define(SHAKE, false);
    }

    public void beginHunt(Player target) {
        targetUuid = target.getUUID();
        entityData.set(TARGET_ID, target.getId());
        entityData.set(PHASE, INTRO);
        phaseTicks = 0;
        setTarget(target);
        setInvisible(true);
        setInvulnerable(true);
        getNavigation().stop();
    }

    public int getTargetEntityId() { return entityData.get(TARGET_ID); }
    public boolean isShakeActive() { return entityData.get(SHAKE); }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        byte phase = entityData.get(PHASE);
        if (phase == INTRO || phase == CAPTURE || phase == DEPART || phase == ESCAPE || isInvulnerable()) return false;
        boolean accepted = super.hurt(source, amount);
        if (accepted && amount > 0.0F && phase == CHASE) {
            damageTaken += amount;
            if (damageTaken >= 500.0F) escapeWithReward();
        }
        return accepted;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        Player target = getHuntTarget();
        byte phase = entityData.get(PHASE);
        if (entityData.get(SHAKE)) {
            shakeTicks++;
            if (shakeTicks >= 12) entityData.set(SHAKE, false);
        }
        if (phase != ESCAPE && phase != WALK_AWAY && (target == null || !target.isAlive())) {
            stopMusic();
            discard();
            return;
        }

        if (phase == INTRO) {
            getNavigation().stop();
            phaseTicks++;
            if (level() instanceof ServerLevel serverLevel && phaseTicks % 4 == 0) {
                DustParticleOptions red = new DustParticleOptions(new Vector3f(0.95F, 0.02F, 0.02F), 1.4F);
                serverLevel.sendParticles(red, getX(), getY() + 1.0D, getZ(), 12, 0.35D, 0.65D, 0.35D, 0.01D);
            }
            if (phaseTicks >= 40) startChase(target);
            return;
        }

        if (phase == CHASE) {
            phaseTicks++;
            if (phaseTicks >= 2400 || distanceToSqr(target) > 120.0D * 120.0D) {
                stopMusic();
                discard();
                return;
            }
            updateChaseSpeed();
            setTarget(target);
            if (tickCount % 10 == 0) breakObstructingBlocks(target);
            if (distanceToSqr(target) <= 2.25D) startCapture(target);
            return;
        }

        if (phase == CAPTURE) {
            getNavigation().stop();
            target.setDeltaMovement(Vec3.ZERO);
            captureTicks++;
            if (captureTicks % 10 == 0 && target.getHealth() > 0.5F) {
                target.setHealth(Math.max(0.5F, target.getHealth() - 2.0F));
            }
            if (target.getHealth() <= 0.5F) {
                ServerPlayer serverPlayer = target instanceof ServerPlayer sp ? sp : null;
                if (serverPlayer != null) {
                    serverPlayer.connection.send(new ClientboundSetActionBarTextPacket(Component.literal("How can you live with yourself?").setStyle(Style.EMPTY.withColor(ChatFormatting.RED))));
                } else {
                    target.displayClientMessage(Component.literal("How can you live with yourself?").setStyle(Style.EMPTY.withColor(ChatFormatting.RED)), true);
                }
                entityData.set(PHASE, DEPART);
                phaseTicks = 0;
            }
            return;
        }

        if (phase == DEPART) {
            phaseTicks++;
            getNavigation().stop();
            if (phaseTicks >= 100) {
                scheduleLingeringEffects(target);
                stopMusic();
                walkAwayFrom(target);
            }
            return;
        }

        if (phase == ESCAPE) {
            phaseTicks++;
            getNavigation().stop();
            if (target == null || !target.isAlive()) {
                discard();
            } else if (phaseTicks >= 40) {
                walkAwayFrom(target);
            }
            return;
        }

        if (phase == WALK_AWAY) {
            phaseTicks++;
            getNavigation().stop();
            setYRot((float) (Math.atan2(-departureDirection.x, departureDirection.z) * (180.0D / Math.PI)));
            setYHeadRot(getYRot());
            Vec3 motion = getDeltaMovement();
            setDeltaMovement(departureDirection.x * 0.12D, motion.y, departureDirection.z * 0.12D);
            if (phaseTicks >= 80) {
                setInvisible(true);
                discard();
            }
        }

    }

    private void startChase(Player target) {
        entityData.set(PHASE, CHASE);
        phaseTicks = 0;
        setInvisible(false);
        setInvulnerable(false);
        setTarget(target);
        getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.20D);
        entityData.set(SHAKE, true);
        shakeTicks = 0;
        if (target instanceof ServerPlayer serverPlayer) {
            serverPlayer.connection.send(new ClientboundSetTitlesAnimationPacket(5, 30, 8));
            serverPlayer.connection.send(new ClientboundSetTitleTextPacket(Component.literal("RUN!").setStyle(Style.EMPTY.withColor(ChatFormatting.RED).withBold(true))));
            DeathBecomesNetwork.sendChaseMusic(serverPlayer, true);
        }
    }

    private void startCapture(Player target) {
        entityData.set(PHASE, CAPTURE);
        captureTicks = 0;
        captureStartedAt = level().getGameTime();
        target.getPersistentData().putLong(DeathBecomes.HEAL_LOCK_KEY, captureStartedAt + 12L * 20L);
        getNavigation().stop();
        target.addEffect(new MobEffectInstance(net.minecraft.world.effect.MobEffects.WEAKNESS, 12 * 20, 4));
        MobEffect stun = ForgeRegistries.MOB_EFFECTS.getValue(new net.minecraft.resources.ResourceLocation("cataclysm", "stun"));
        if (stun != null) target.addEffect(new MobEffectInstance(stun, 12 * 20, 0));
        target.setHealth(Math.max(0.5F, target.getHealth() * 0.10F));
    }

    private void scheduleLingeringEffects(Player target) {
        long stunEnd = captureStartedAt + 12L * 20L;
        target.getPersistentData().putLong("DeathBecomesPendingEffectsAt", Math.max(level().getGameTime(), stunEnd));
    }

    private void updateChaseSpeed() {
        double progress = Math.min(1.0D, phaseTicks / 2400.0D);
        var speed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.setBaseValue(0.20D + 0.12D * progress);
    }

    private void walkAwayFrom(Player target) {
        Vec3 away = position().subtract(target.position());
        if (away.lengthSqr() < 1.0E-5D) away = new Vec3(0.0D, 0.0D, 1.0D);
        away = away.normalize();
        departureDirection = away;
        float yaw = (float) (Math.atan2(-away.x, away.z) * (180.0D / Math.PI));
        setYRot(yaw);
        setYHeadRot(yaw);
        setTarget(null);
        var speed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.setBaseValue(0.20D);
        entityData.set(PHASE, WALK_AWAY);
        phaseTicks = 0;
        getNavigation().stop();
    }

    private void breakObstructingBlocks(Player target) {
        Vec3 direction = target.position().subtract(position());
        if (direction.lengthSqr() < 1.0E-5D) return;
        direction = direction.normalize();
        BlockPos feet = BlockPos.containing(position().add(direction.scale(0.9D)).add(0.0D, 0.2D, 0.0D));
        for (int dy = 0; dy < 2; dy++) {
            BlockPos pos = feet.above(dy);
            BlockState state = level().getBlockState(pos);
            if (state.isAir() || state.hasBlockEntity() || state.getDestroySpeed(level(), pos) < 0.0F || state.getDestroySpeed(level(), pos) > 50.0F) continue;
            level().destroyBlock(pos, true, this);
        }
    }

    private void escapeWithReward() {
        if (!rewardGranted) {
            spawnAtLocation(ModItems.HEART_FRAGMENT.get());
            rewardGranted = true;
        }
        stopMusic();
        entityData.set(PHASE, ESCAPE);
        setInvisible(false);
        setInvulnerable(true);
        phaseTicks = 0;
        Component line = Component.literal("...This isn't worth it. I'll be back.")
                .setStyle(Style.EMPTY.withColor(ChatFormatting.RED).withItalic(true));
        Player target = getHuntTarget();
        if (target instanceof ServerPlayer serverPlayer) {
            serverPlayer.connection.send(new ClientboundSetActionBarTextPacket(line));
        } else if (target != null) {
            target.displayClientMessage(line, true);
        }
        getNavigation().stop();
    }

    private void stopMusic() {
        Player target = getHuntTarget();
        if (target instanceof ServerPlayer serverPlayer) DeathBecomesNetwork.sendChaseMusic(serverPlayer, false);
    }

    private Player getHuntTarget() {
        if (targetUuid == null || !(level() instanceof ServerLevel serverLevel)) return getTarget() instanceof Player p ? p : null;
        return serverLevel.getPlayerByUUID(targetUuid);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (targetUuid != null) tag.putUUID("Target", targetUuid);
        tag.putByte("Phase", entityData.get(PHASE));
        tag.putInt("PhaseTicks", phaseTicks);
        tag.putInt("CaptureTicks", captureTicks);
        tag.putFloat("DamageTaken", damageTaken);
        tag.putLong("CaptureStartedAt", captureStartedAt);
        tag.putBoolean("RewardGranted", rewardGranted);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("Target")) targetUuid = tag.getUUID("Target");
        entityData.set(PHASE, tag.getByte("Phase"));
        phaseTicks = tag.getInt("PhaseTicks");
        captureTicks = tag.getInt("CaptureTicks");
        damageTaken = tag.getFloat("DamageTaken");
        captureStartedAt = tag.getLong("CaptureStartedAt");
        rewardGranted = tag.getBoolean("RewardGranted");
    }

}
