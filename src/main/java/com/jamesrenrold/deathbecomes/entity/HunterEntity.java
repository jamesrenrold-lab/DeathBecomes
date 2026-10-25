package com.jamesrenrold.deathbecomes.entity;

import com.jamesrenrold.deathbecomes.DeathBecomes;
import com.jamesrenrold.deathbecomes.item.ModItems;
import com.jamesrenrold.deathbecomes.item.HeartFragmentItem;
import com.jamesrenrold.deathbecomes.network.DeathBecomesNetwork;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Vector3f;

import java.util.UUID;
import java.util.ArrayList;
import java.util.List;

public class HunterEntity extends Monster {
    private static final EntityDataAccessor<Integer> TARGET_ID = SynchedEntityData.defineId(HunterEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> PHASE = SynchedEntityData.defineId(HunterEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> SHAKE = SynchedEntityData.defineId(HunterEntity.class, EntityDataSerializers.BOOLEAN);
    private static final byte INTRO = 0, CHASE = 1, CAPTURE = 2, DEPART = 3, ESCAPE = 4, WALK_AWAY = 5,
            COUNTERATTACK = 6, THROW = 7;
    private UUID targetUuid;
    private int phaseTicks;
    private int captureTicks;
    private int shakeTicks;
    private float damageTaken;
    private long captureStartedAt;
    private boolean rewardGranted;
    private Vec3 departureDirection = new Vec3(0.0D, 0.0D, 1.0D);
    private int stolenExperience;
    private boolean captureHadHeart;
    private boolean killAfterStare;
    private boolean climbing;
    private int leapWindupTicks;
    private int leapFlightTicks;
    private int leapCooldownTicks;
    private int counterCatchCount;
    private int counterWindowTicks;
    private double counterX;
    private double counterY;
    private double counterZ;
    private int quickRampTicks = -1;
    private boolean speedRampAtMaximum;
    private int counterRecoveryTicks;
    private int throwTicks;
    private double throwStartX;
    private double throwStartY;
    private double throwStartZ;
    private double throwEndX;
    private double throwEndY;
    private double throwEndZ;
    private final List<BlockPos> temporaryBoneWall = new ArrayList<>();

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
        boolean counterWindowOpen = phase == COUNTERATTACK && counterWindowTicks > 0;
        if (phase == INTRO || phase == CAPTURE || phase == DEPART || phase == ESCAPE
                || (phase == COUNTERATTACK && !counterWindowOpen) || isInvulnerable()) return false;
        boolean accepted = super.hurt(source, amount);
        if (accepted && amount > 0.0F && (phase == CHASE || counterWindowOpen)) {
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
            removeTemporaryBoneWall();
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
            if (leapCooldownTicks > 0) leapCooldownTicks--;
            if (counterRecoveryTicks > 0) counterRecoveryTicks--;
            boolean elevatedTarget = target.getY() > getY() + 1.2D;
            boolean directClimbPath = elevatedTarget && hasDirectClimbPath(target);
            boolean navigablePath = elevatedTarget && !directClimbPath && hasNavigablePath(target);
            boolean shouldLeap = elevatedTarget && !directClimbPath && !navigablePath;
            if (leapWindupTicks > 0 && shouldLeap) {
                tickLeapWindup(target);
                return;
            } else if (leapWindupTicks > 0) {
                leapWindupTicks = 0;
                setGlowingTag(false);
            }
            if (leapFlightTicks > 0) {
                leapFlightTicks--;
                getNavigation().stop();
                if (distanceToSqr(target) <= 2.25D) startCapture(target);
                return;
            }
            if (elevatedTarget) {
                if (directClimbPath) {
                    climbing = true;
                    climbToward(target);
                } else {
                    climbing = false;
                    if (shouldLeap && leapCooldownTicks <= 0) beginLeapWindup();
                }
            } else {
                climbing = false;
                leapWindupTicks = 0;
                setGlowingTag(false);
            }
            if (counterRecoveryTicks <= 0 && distanceToSqr(target) <= 2.25D) startCapture(target);
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
                showActionbar(target, captureHadHeart ? "Told you I'd be back." : "How can you live with yourself?");
                killAfterStare = captureHadHeart;
                entityData.set(PHASE, DEPART);
                phaseTicks = 0;
            }
            return;
        }

        if (phase == COUNTERATTACK) {
            getNavigation().stop();
            target.setPos(counterX, counterY, counterZ);
            target.setDeltaMovement(Vec3.ZERO);
            target.setSprinting(false);
            counterWindowTicks--;
            if (counterWindowTicks == 60) showActionbar(target, "You'll run out of energy soon enough.");
            if (counterWindowTicks <= 0) beginThrow(target);
            return;
        }

        if (phase == THROW) {
            getNavigation().stop();
            throwTicks++;
            double progress = Math.min(1.0D, throwTicks / 12.0D);
            target.setPos(throwStartX + (throwEndX - throwStartX) * progress,
                    throwStartY + (throwEndY - throwStartY) * progress,
                    throwStartZ + (throwEndZ - throwStartZ) * progress);
            target.setDeltaMovement(Vec3.ZERO);
            target.setSprinting(false);
            target.fallDistance = 0.0F;
            if (throwTicks >= 12) {
                removeTemporaryBoneWall();
                resumeChaseAfterThrow(target);
            }
            return;
        }

        if (phase == DEPART) {
            phaseTicks++;
            getNavigation().stop();
            if (killAfterStare && target != null) {
                faceTarget(target);
                if (phaseTicks >= 40) {
                    scheduleLingeringEffects(target);
                    HeartFragmentItem.removeHeart(target);
                    target.hurt(damageSources().mobAttack(this), Math.max(1.0F, target.getHealth() + 1.0F));
                    if (target.isAlive()) target.setHealth(0.0F);
                    killAfterStare = false;
                    stopMusic();
                    walkAwayFrom(target);
                }
            } else if (phaseTicks >= 100) {
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
        leapWindupTicks = 0;
        leapFlightTicks = 0;
        leapCooldownTicks = 0;
        counterRecoveryTicks = 0;
        climbing = false;
        setGlowingTag(false);
        entityData.set(SHAKE, true);
        shakeTicks = 0;
        if (target instanceof ServerPlayer serverPlayer) {
            serverPlayer.connection.send(new ClientboundSetTitlesAnimationPacket(5, 30, 8));
            serverPlayer.connection.send(new ClientboundSetTitleTextPacket(Component.literal("RUN!").setStyle(Style.EMPTY.withColor(ChatFormatting.RED).withBold(true))));
            DeathBecomesNetwork.sendChaseMusic(serverPlayer, true);
        }
    }

    private void startCapture(Player target) {
        if (counterCatchCount < 3) {
            beginCounterattack(target);
            return;
        }

        beginFinalCapture(target);
    }

    private void beginCounterattack(Player target) {
        counterCatchCount++;
        counterWindowTicks = 80;
        counterX = target.getX();
        counterY = target.getY();
        counterZ = target.getZ();
        captureStartedAt = level().getGameTime();
        target.getPersistentData().putLong(DeathBecomes.HEAL_LOCK_KEY, captureStartedAt + 12L * 20L);
        DeathBecomes.applyManaDebuff(target);
        target.addEffect(new MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 4 * 20, 4));
        target.setSprinting(false);
        target.setDeltaMovement(Vec3.ZERO);
        setTarget(null);
        getNavigation().stop();
        entityData.set(PHASE, COUNTERATTACK);
        phaseTicks = 0;
        setInvulnerable(false);
        var speed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.setBaseValue(0.0D);
    }

    private void beginFinalCapture(Player target) {
        entityData.set(PHASE, CAPTURE);
        captureTicks = 0;
        captureStartedAt = level().getGameTime();
        captureHadHeart = HeartFragmentItem.getHeartCount(target) > 0;
        if (!captureHadHeart) {
            int xpToSteal = Math.max(0, target.totalExperience / 3);
            if (xpToSteal > 0) {
                target.giveExperiencePoints(-xpToSteal);
                stolenExperience += xpToSteal;
            }
        }
        DeathBecomes.applyManaDebuff(target);
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

    private void beginThrow(Player target) {
        Vec3 direction = new Vec3(target.getX() - getX(), 0.0D, target.getZ() - getZ());
        if (direction.lengthSqr() < 1.0E-5D) direction = new Vec3(0.0D, 0.0D, 1.0D);
        direction = direction.normalize();
        target.setHealth(Math.max(0.5F, target.getHealth() * 0.60F));
        double wallDistance = findWallDistance(target, direction);
        if (wallDistance < 0.0D) {
            wallDistance = 8.0D;
            createTemporaryBoneWall(target, direction, wallDistance);
        }

        throwStartX = target.getX();
        throwStartY = target.getY();
        throwStartZ = target.getZ();
        double throwDistance = Math.max(0.2D, wallDistance - 0.8D);
        throwEndX = throwStartX + direction.x * throwDistance;
        throwEndY = throwStartY + 0.1D;
        throwEndZ = throwStartZ + direction.z * throwDistance;
        throwTicks = 0;
        entityData.set(PHASE, THROW);
        phaseTicks = 0;
        setTarget(null);
        setInvulnerable(true);
        var speed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.setBaseValue(0.0D);
        getNavigation().stop();
    }

    private void resumeChaseAfterThrow(Player target) {
        quickRampTicks = 0;
        speedRampAtMaximum = false;
        counterRecoveryTicks = 20;
        phaseTicks = 0;
        entityData.set(PHASE, CHASE);
        setInvisible(false);
        setInvulnerable(false);
        setTarget(target);
        getNavigation().stop();
    }

    private double findWallDistance(Player target, Vec3 direction) {
        for (int distance = 5; distance <= 10; distance++) {
            for (int height = 0; height < 3; height++) {
                BlockPos probe = BlockPos.containing(target.getX() + direction.x * distance,
                        target.getY() + height + 0.2D, target.getZ() + direction.z * distance);
                if (!level().getBlockState(probe).getCollisionShape(level(), probe).isEmpty()) return distance;
            }
        }
        return -1.0D;
    }

    private void createTemporaryBoneWall(Player target, Vec3 direction, double distance) {
        Vec3 side = new Vec3(-direction.z, 0.0D, direction.x);
        double wallX = target.getX() + direction.x * distance;
        double wallZ = target.getZ() + direction.z * distance;
        for (int lateral = -1; lateral <= 1; lateral++) {
            BlockPos base = BlockPos.containing(wallX + side.x * lateral, target.getY(), wallZ + side.z * lateral);
            for (int height = 0; height < 3; height++) {
                BlockPos pos = base.above(height);
                var state = level().getBlockState(pos);
                if (state.isAir() || state.canBeReplaced()) {
                    if (level().setBlockAndUpdate(pos, Blocks.BONE_BLOCK.defaultBlockState())) {
                        temporaryBoneWall.add(pos.immutable());
                    }
                }
            }
        }
    }

    private void removeTemporaryBoneWall() {
        for (BlockPos pos : temporaryBoneWall) {
            if (level().getBlockState(pos).is(Blocks.BONE_BLOCK)) {
                level().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            }
        }
        temporaryBoneWall.clear();
    }

    private void updateChaseSpeed() {
        double progress = Math.min(1.0D, phaseTicks / 2400.0D);
        var speed = getAttribute(Attributes.MOVEMENT_SPEED);
        Player target = getHuntTarget();
        int bonusHearts = target == null ? 0 : HeartFragmentItem.getHeartCount(target);
        double heartMultiplier = Math.pow(1.2D, bonusHearts);
        double chaseSpeed;
        if (quickRampTicks >= 0) {
            int elapsed = quickRampTicks++;
            if (elapsed < 20) {
                chaseSpeed = 0.20D;
            } else {
                chaseSpeed = 0.20D + 0.12D * Math.min(1.0D, (elapsed - 20) / 200.0D);
                if (elapsed >= 220) {
                    quickRampTicks = -1;
                    speedRampAtMaximum = true;
                }
            }
        } else if (speedRampAtMaximum) {
            chaseSpeed = 0.32D;
        } else {
            chaseSpeed = 0.20D + 0.12D * progress;
        }
        if (speed != null) speed.setBaseValue(chaseSpeed * heartMultiplier);
    }

    private boolean hasDirectClimbPath(Player target) {
        Vec3 toward = target.position().subtract(position());
        Vec3 horizontal = new Vec3(toward.x, 0.0D, toward.z);
        if (horizontal.lengthSqr() < 1.0E-5D) return false;
        horizontal = horizontal.normalize();
        Vec3 wallProbe = position().add(horizontal.scale(getBbWidth() * 0.5D + 0.12D))
                .add(0.0D, getBbHeight() * 0.55D, 0.0D);
        BlockPos wallPos = BlockPos.containing(wallProbe);
        boolean hasWall = !level().getBlockState(wallPos).getCollisionShape(level(), wallPos).isEmpty();
        AABB raisedBox = getBoundingBox().move(0.0D, 0.48D, 0.0D);
        return hasWall && level().noCollision(this, raisedBox);
    }

    private boolean hasNavigablePath(Player target) {
        var path = getNavigation().createPath(target, 0);
        return path != null && path.canReach();
    }

    private void climbToward(Player target) {
        getNavigation().stop();
        faceTarget(target);
        Vec3 toward = target.position().subtract(position());
        Vec3 horizontal = new Vec3(toward.x, 0.0D, toward.z);
        if (horizontal.lengthSqr() > 1.0E-5D) horizontal = horizontal.normalize();
        Vec3 climbStep = new Vec3(horizontal.x * 0.10D, 0.48D, horizontal.z * 0.10D);
        move(MoverType.SELF, climbStep);
        setDeltaMovement(0.0D, 0.0D, 0.0D);
    }

    private void beginLeapWindup() {
        leapWindupTicks = 1;
        setGlowingTag(true);
        getNavigation().stop();
    }

    private void tickLeapWindup(Player target) {
        getNavigation().stop();
        faceTarget(target);
        leapWindupTicks++;
        if (level() instanceof ServerLevel serverLevel) {
            DustParticleOptions red = new DustParticleOptions(new Vector3f(0.95F, 0.02F, 0.02F), 1.8F);
            serverLevel.sendParticles(red, getX(), getY() + getBbHeight() * 0.55D, getZ(), 10,
                    0.35D, 0.55D, 0.35D, 0.02D);
        }
        if (leapWindupTicks == 10) showActionbar(target, "Oh no you don't.");
        if (leapWindupTicks >= 20) leapAt(target);
    }

    private void leapAt(Player target) {
        Vec3 toward = target.position().subtract(position());
        Vec3 horizontal = new Vec3(toward.x, 0.0D, toward.z);
        double horizontalDistance = horizontal.length();
        if (horizontalDistance < 1.0E-5D) {
            horizontal = new Vec3(0.0D, 0.0D, 1.0D);
            horizontalDistance = 0.0D;
        } else {
            horizontal = horizontal.scale(1.0D / horizontalDistance);
        }
        int flightDuration = Math.min(32, Math.max(12, 12 + (int) Math.ceil(horizontalDistance * 1.1D)));
        double horizontalTravelFactor = 0.0D;
        double drag = 1.0D;
        for (int tick = 0; tick < flightDuration; tick++) {
            horizontalTravelFactor += drag;
            drag *= 0.91D;
        }
        double horizontalSpeed = Math.min(3.0D, Math.max(0.35D, horizontalDistance / horizontalTravelFactor));
        double verticalSpeed = Math.min(1.65D, Math.max(0.72D,
                0.62D + Math.max(0.0D, toward.y) * 0.12D + horizontalDistance * 0.025D));
        setDeltaMovement(horizontal.x * horizontalSpeed, verticalSpeed, horizontal.z * horizontalSpeed);
        setGlowingTag(false);
        leapWindupTicks = 0;
        leapFlightTicks = flightDuration;
        leapCooldownTicks = 60;
        climbing = false;
    }

    private void faceTarget(Player target) {
        Vec3 toward = target.position().subtract(position());
        if (toward.lengthSqr() < 1.0E-5D) return;
        float yaw = (float) (Math.atan2(-toward.x, toward.z) * (180.0D / Math.PI));
        setYRot(yaw);
        setYHeadRot(yaw);
    }

    private void showActionbar(Player target, String message) {
        Component line = Component.literal(message).setStyle(Style.EMPTY.withColor(ChatFormatting.RED));
        if (target instanceof ServerPlayer serverPlayer) {
            serverPlayer.connection.send(new ClientboundSetActionBarTextPacket(line));
        } else {
            target.displayClientMessage(line, true);
        }
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
        tag.putInt("StolenExperience", stolenExperience);
        tag.putBoolean("CaptureHadHeart", captureHadHeart);
        tag.putBoolean("KillAfterStare", killAfterStare);
        tag.putInt("CounterCatchCount", counterCatchCount);
        tag.putInt("CounterWindowTicks", counterWindowTicks);
        tag.putDouble("CounterX", counterX);
        tag.putDouble("CounterY", counterY);
        tag.putDouble("CounterZ", counterZ);
        tag.putInt("QuickRampTicks", quickRampTicks);
        tag.putBoolean("SpeedRampAtMaximum", speedRampAtMaximum);
        tag.putInt("CounterRecoveryTicks", counterRecoveryTicks);
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
        stolenExperience = tag.getInt("StolenExperience");
        captureHadHeart = tag.getBoolean("CaptureHadHeart");
        killAfterStare = tag.getBoolean("KillAfterStare");
        counterCatchCount = tag.getInt("CounterCatchCount");
        counterWindowTicks = tag.getInt("CounterWindowTicks");
        counterX = tag.getDouble("CounterX");
        counterY = tag.getDouble("CounterY");
        counterZ = tag.getDouble("CounterZ");
        if (tag.contains("QuickRampTicks")) quickRampTicks = tag.getInt("QuickRampTicks");
        speedRampAtMaximum = tag.getBoolean("SpeedRampAtMaximum");
        counterRecoveryTicks = tag.getInt("CounterRecoveryTicks");
    }

}
