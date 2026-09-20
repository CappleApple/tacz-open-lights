package com.cappleapple.taczflashierflashlights.smoke;

import com.cappleapple.taczflashierflashlights.client.ClientEvents;
import com.cappleapple.taczflashierflashlights.client.WeaponLightManager;
import com.cappleapple.taczflashierflashlights.network.FlashlightNetwork;
import com.cappleapple.openlights.client.render.OpenLightRenderer;
import com.cappleapple.openlights.client.particle.BeamDustParticles;
import com.cappleapple.openlights.beam.BeamProfile;
import com.cappleapple.openlights.beam.BeamProfiles;
import com.cappleapple.openlights.api.client.LightDefinition;
import com.cappleapple.openlights.api.client.LightHandle;
import com.cappleapple.openlights.api.client.LightKey;
import com.cappleapple.openlights.client.scene.SceneCache;
import com.cappleapple.openlights.client.scene.SceneSnapshot;
import com.cappleapple.openlights.content.FlashlightItem;
import com.cappleapple.openlights.content.LightShape;
import com.cappleapple.openlights.content.LightSourceBlock;
import com.cappleapple.openlights.content.LightSourceBlockEntity;
import com.cappleapple.openlights.content.ModContent;
import com.cappleapple.openlights.api.client.OpenLightsApi;
import com.mojang.logging.LogUtils;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Included only by the development smoke source set; never packaged in the release jar. */
@Mod.EventBusSubscriber(modid = "taczopenlights", value = Dist.CLIENT)
public final class ClientSmokeHarness {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long STARTED = System.nanoTime();
    private static final String WORLD = "SmokeWorld";
    private static final String SMOKE_PACK_NAME = "openlights_smoke_" + Long.toUnsignedString(STARTED);
    private static final ResourceLocation LOPRO = new ResourceLocation("tacz", "laser_lopro");
    private static long profileRevision;
    private static Path smokePackDirectory;
    private static Stage stage = Stage.WAIT_WORLD;
    private static boolean loading;
    private static boolean failed;
    private static long stageStarted = STARTED;
    private static int stageTicks;
    private static int stableFrames;
    private static long stageShadowPasses;
    private static LightHandle apiLight;
    private static final Vec3 API_SOURCE = new Vec3(.5, 162.5, 3.5);
    private static final BlockPos OCCLUDER = new BlockPos(0, 162, 6);
    private static final int PERFORMANCE_FRAMES = 180;
    private static final List<BlockPos> NATIVE_POSITIONS = List.of(
            new BlockPos(-3,162,4), new BlockPos(0,162,4), new BlockPos(3,162,4));
    private static final BlockPos WATER_POSITION = new BlockPos(-2,162,6);
    private static final BlockPos ICE_POSITION = new BlockPos(2,162,6);
    private static final List<BlockPos> WATER_CONTAINER = List.of(
            WATER_POSITION.below(), WATER_POSITION.west(), WATER_POSITION.east(),
            WATER_POSITION.north(), WATER_POSITION.south());
    private static final List<LightHandle> performanceLights = new ArrayList<>();
    private static final List<Double> cpuSamples = new ArrayList<>();
    private static final List<Double> gpuSamples = new ArrayList<>();
    private static final List<Double> shadowSamples = new ArrayList<>();
    private static long occluderRevision;
    private static int trianglesWithoutOccluder;
    private static int movingFrame;
    private static LightHandle shadowA;
    private static LightHandle shadowB;
    private static LightDefinition shadowADefinition;
    private static Object firstShadowARecord;
    private static Object preResizeShadowRecord;
    private static int originalMaxLights;
    private static int originalMaxShadowLights;
    private static int originalShadowResolution;
    private static CompletableFuture<Void> fixtureUpdate = CompletableFuture.completedFuture(null);

    private ClientSmokeHarness() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || failed
                || !System.getProperty("taczopenlights.compatSmoke", "").isEmpty()) return;
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.options.pauseOnLostFocus = false;
        minecraft.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.0);
        minecraft.mouseHandler.releaseMouse();

        if (stage == Stage.WAIT_WORLD) {
            if (!loading && minecraft.screen instanceof TitleScreen title) {
                loading = true;
                hideWindow(minecraft);
                minecraft.options.renderDistance().set(6);
                minecraft.options.simulationDistance().set(5);
                minecraft.options.framerateLimit().set(60);
                minecraft.options.hideGui = false;
                minecraft.getTutorial().setStep(TutorialSteps.NONE);
                prepareDatapack();
                LOGGER.info("SMOKE_START loading existing world {}", WORLD);
                minecraft.createWorldOpenFlows().loadLevel(title, WORLD);
            }
            if (minecraft.level != null && minecraft.player != null
                    && minecraft.getSingleplayerServer() != null && minecraft.screen == null) {
                minecraft.options.setCameraType(CameraType.FIRST_PERSON);
                originalMaxLights = com.cappleapple.openlights.config.ClientConfig.MAX_LIGHTS.get();
                originalMaxShadowLights = com.cappleapple.openlights.config.ClientConfig.MAX_SHADOW_LIGHTS.get();
                originalShadowResolution = com.cappleapple.openlights.config.ClientConfig.SHADOW_RESOLUTION.get();
                prepareWorld();
                begin(Stage.PROFILE_LOGIN);
                equip("m4a1", "laser_peq15");
            } else if (secondsSince(STARTED) > 180) {
                fail("World did not become ready within 180 seconds; screen=" + minecraft.screen);
            }
            return;
        }

        stageTicks++;
        if (fixtureUpdate.isCompletedExceptionally()) {
            try {
                fixtureUpdate.join();
            } catch (RuntimeException exception) {
                fail("Authoritative fixture update failed", exception);
            }
        }
        if (stage == Stage.FINISHED) {
            if (stageTicks >= 40) minecraft.stop();
        } else if (secondsSince(stageStarted) > 45) {
            fail("Stage timed out: " + diagnostics());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || failed || stage == Stage.WAIT_WORLD
                || stage == Stage.FINISHED) return;
        if (stage == Stage.PERFORMANCE_MOVING) movePerformanceLights();
        stageShadowPasses += OpenLightRenderer.statistics().shadowPasses();
        if (stageTicks < 40 || !fixtureUpdate.isDone()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.screen != null) return;

        if (!stageCondition()) {
            stableFrames = 0;
            return;
        }
        if (stage == Stage.PERFORMANCE_STATIC || stage == Stage.PERFORMANCE_MOVING) {
            if (!samplePerformance()) return;
        } else if (++stableFrames < 8) return;

        LOGGER.info("SMOKE_PASS {} {}", stage, diagnostics());
        Screenshot.grab(minecraft.gameDirectory, "smoke-" + stage.ordinal() + "-"
                        + stage.name().toLowerCase(java.util.Locale.ROOT) + ".png",
                minecraft.getMainRenderTarget(), message -> LOGGER.info("SMOKE_SCREENSHOT {}", message.getString()));
        switch (stage) {
            case PROFILE_LOGIN -> begin(Stage.PEQ15_ON);
            case PEQ15_ON -> {
                begin(Stage.PEQ15_OFF);
                KeyMapping.click(ClientEvents.TOGGLE.getKey());
            }
            case PEQ15_OFF -> {
                begin(Stage.PEQ15_REENABLED);
                KeyMapping.click(ClientEvents.TOGGLE.getKey());
            }
            case PEQ15_REENABLED -> {
                begin(Stage.PEQ6_ON);
                equip("hk_mk23", "laser_peq6");
            }
            case PEQ6_ON -> {
                begin(Stage.NIGHTSTICK_ON);
                equip("glock_17", "laser_nightstick");
            }
            case NIGHTSTICK_ON -> {
                begin(Stage.HIDDEN_GUI);
                minecraft.options.hideGui = true;
            }
            case HIDDEN_GUI -> {
                begin(Stage.THIRD_PERSON);
                minecraft.options.hideGui = false;
                minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            }
            case THIRD_PERSON -> {
                begin(Stage.ATTACHMENT_REMOVED);
                equip("glock_17", null);
            }
            case ATTACHMENT_REMOVED -> {
                begin(Stage.EMPTY_HAND);
                equip(null, null);
            }
            case EMPTY_HAND -> {
                begin(Stage.API_POINT);
                minecraft.options.setCameraType(CameraType.FIRST_PERSON);
                prepareApiSource();
                apiLight = OpenLightsApi.create(new ResourceLocation("openlights", "smoke"),
                        new LightDefinition.Point(API_SOURCE, new Vec3(1,1,1), 2, 16, true, .4F));
            }
            case API_POINT -> {
                begin(Stage.API_SPOT);
                apiLight.update(new LightDefinition.Spot(API_SOURCE, new Vec3(0,0,1), new Vec3(0,1,0),
                        new Vec3(1,1,1), 2, 16, 55, 38, true, .4F));
            }
            case API_SPOT -> {
                begin(Stage.API_AREA);
                apiLight.update(new LightDefinition.Area(API_SOURCE, new Vec3(0,0,1), new Vec3(0,1,0),
                        new Vec3(1,1,1), 2, 16, 2, 1, 70, true, .4F));
            }
            case API_AREA -> {
                occluderRevision = uploadedSceneRevision();
                trianglesWithoutOccluder = OpenLightRenderer.statistics().triangles();
                begin(Stage.OCCLUDER_ADDED);
                setOccluder(true);
            }
            case OCCLUDER_ADDED -> {
                occluderRevision = uploadedSceneRevision();
                begin(Stage.OCCLUDER_REMOVED);
                setOccluder(false);
            }
            case OCCLUDER_REMOVED -> {
                begin(Stage.API_CLOSED);
                apiLight.close();
            }
            case API_CLOSED -> {
                begin(Stage.PERFORMANCE_STATIC);
                createPerformanceLights();
            }
            case PERFORMANCE_STATIC -> {
                begin(Stage.PERFORMANCE_MOVING);
                resetPerformanceSamples();
            }
            case PERFORMANCE_MOVING -> {
                performanceLights.forEach(LightHandle::close);
                performanceLights.clear();
                begin(Stage.NATIVE_HANDHELD_ON);
                useNativeFlashlight(true);
            }
            case NATIVE_HANDHELD_ON -> {
                begin(Stage.NATIVE_HANDHELD_OFF);
                useNativeFlashlight(false);
            }
            case NATIVE_HANDHELD_OFF -> {
                begin(Stage.NATIVE_BLOCKS_ON);
                placeNativeBlocks();
            }
            case NATIVE_BLOCKS_ON -> {
                begin(Stage.NATIVE_BLOCKS_DYED_RANGE);
                interactNativeBlocks(true, true);
            }
            case NATIVE_BLOCKS_DYED_RANGE -> {
                begin(Stage.NATIVE_BLOCKS_OFF);
                interactNativeBlocks(false, false);
            }
            case NATIVE_BLOCKS_OFF -> {
                begin(Stage.WATER_ICE_MEDIA);
                interactNativeBlocks(false, false);
                prepareWaterIce();
            }
            case WATER_ICE_MEDIA -> {
                begin(Stage.CONTENT_CLEANUP);
                contentAction(ClientSmokeHarness::clearNativeContent);
            }
            case CONTENT_CLEANUP -> {
                begin(Stage.SHADOW_A_SELECTED);
                createShadowRegressionLights();
            }
            case SHADOW_A_SELECTED -> {
                firstShadowARecord = shadowRecords().get(shadowA.key());
                begin(Stage.SHADOW_B_SELECTED);
                shadowB.update(shadowRegressionDefinition(new Vec3(.5,162.5,1.5)));
            }
            case SHADOW_B_SELECTED -> {
                begin(Stage.SHADOW_A_RESELECTED);
                shadowB.update(shadowRegressionDefinition(new Vec3(4.5,162.5,5.5)));
            }
            case SHADOW_A_RESELECTED -> {
                preResizeShadowRecord = shadowRecords().get(shadowA.key());
                begin(Stage.SHADOW_RESIZE_128);
                shadowB.close();
                com.cappleapple.openlights.config.ClientConfig.SHADOW_RESOLUTION.set(128);
            }
            case SHADOW_RESIZE_128 -> {
                preResizeShadowRecord = shadowRecords().get(shadowA.key());
                begin(Stage.SHADOW_RESIZE_256);
                com.cappleapple.openlights.config.ClientConfig.SHADOW_RESOLUTION.set(256);
            }
            case SHADOW_RESIZE_256 -> {
                shadowA.close();
                shadowB.close();
                com.cappleapple.openlights.config.ClientConfig.MAX_LIGHTS.set(originalMaxLights);
                com.cappleapple.openlights.config.ClientConfig.MAX_SHADOW_LIGHTS.set(originalMaxShadowLights);
                com.cappleapple.openlights.config.ClientConfig.SHADOW_RESOLUTION.set(originalShadowResolution);
                begin(Stage.LOPRO_FIRST_PERSON);
                minecraft.options.setCameraType(CameraType.FIRST_PERSON);
                equip("m4a1", "laser_lopro");
            }
            case LOPRO_FIRST_PERSON -> {
                begin(Stage.LOPRO_THIRD_PERSON);
                minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            }
            case LOPRO_THIRD_PERSON -> {
                profileRevision = BeamProfiles.clientRevision();
                begin(Stage.PROFILE_RELOADED);
                minecraft.options.setCameraType(CameraType.FIRST_PERSON);
                reloadProfile(true);
            }
            case PROFILE_RELOADED -> {
                profileRevision = BeamProfiles.clientRevision();
                begin(Stage.PROFILE_REMOVED);
                reloadProfile(false);
            }
            case PROFILE_REMOVED -> {
                begin(Stage.GRAZING_SHADOWS);
                prepareGrazingScene();
                apiLight = OpenLightsApi.create(new ResourceLocation("openlights", "smoke_grazing"),
                        grazingLight(true));
            }
            case GRAZING_SHADOWS -> {
                begin(Stage.GRAZING_UNSHADOWED);
                apiLight.update(grazingLight(false));
            }
            case GRAZING_UNSHADOWED -> {
                apiLight.close();
                begin(Stage.FINISHED);
                LOGGER.info("SMOKE_SUCCESS verified model captures, weapon toggles, first/third person and F1, "
                        + "attachment cleanup, Point/Spot/Area API handles, stained-glass source media, "
                        + "occluder invalidation, eight-light performance, native handheld and block interactions, "
                        + "water/ice volumes, content cleanup, shadow-slot reassignment, shadow-target resizing, "
                        + "profile login synchronization and defaults, Lopro first/third-person alignment, "
                        + "live profile reload and dust, removed-profile fallback, and paired grazing-shadow screenshots. "
                        + "Grazing screenshot quality requires visual review.");
            }
            default -> throw new IllegalStateException("Unexpected smoke stage " + stage);
        }
    }

    private static boolean stageCondition() {
        Minecraft minecraft = Minecraft.getInstance();
        UUID player = minecraft.player.getUUID();
        boolean weaponBeam = WeaponLightManager.activeLights().stream().anyMatch(beam -> beam.id().equals(player));
        boolean backendBeam = OpenLightRenderer.frameLights().keySet().stream().anyMatch(key -> key.id().equals(player));
        if (!FlashlightNetwork.serverPresent() || rendererFailed()) return false;
        if (stage == Stage.PROFILE_LOGIN) {
            return profilesSynchronized() && BeamProfiles.clientRevision() > 0
                    && expectedProfile(false).equals(BeamProfiles.clientProfile(LOPRO))
                    && BeamProfiles.clientSnapshot().containsKey(LOPRO)
                    && BeamProfiles.clientProfile(new ResourceLocation("openlights", "smoke_missing_profile"))
                        .equals(BeamProfiles.clientDefaults());
        }
        if (stage == Stage.LOPRO_FIRST_PERSON || stage == Stage.LOPRO_THIRD_PERSON
                || stage == Stage.PROFILE_RELOADED || stage == Stage.PROFILE_REMOVED) {
            return loproCondition(player);
        }
        if (stage == Stage.GRAZING_SHADOWS || stage == Stage.GRAZING_UNSHADOWED) {
            boolean shadows = stage == Stage.GRAZING_SHADOWS;
            return minecraft.player.getMainHandItem().isEmpty()
                    && OpenLightRenderer.frameLights().size() == 1
                    && grazingLight(shadows).equals(OpenLightRenderer.frameLights().get(apiLight.key()))
                    && minecraft.level.getBlockState(new BlockPos(0,161,7)).is(Blocks.STONE_BRICKS)
                    && OpenLightRenderer.statistics().triangles() > 0
                    && (shadows ? stageShadowPasses > 0 && shadowRecords().containsKey(apiLight.key())
                        : !shadowRecords().containsKey(apiLight.key()));
        }
        if (stage == Stage.SHADOW_A_SELECTED || stage == Stage.SHADOW_B_SELECTED
                || stage == Stage.SHADOW_A_RESELECTED || stage == Stage.SHADOW_RESIZE_128
                || stage == Stage.SHADOW_RESIZE_256) {
            boolean resized = stage == Stage.SHADOW_RESIZE_128 || stage == Stage.SHADOW_RESIZE_256;
            LightKey selected = stage == Stage.SHADOW_B_SELECTED ? shadowB.key() : shadowA.key();
            Map<?, ?> shadows = shadowRecords();
            Object record = shadows.get(selected);
            if (!shadows.keySet().equals(Set.of(selected)) || record == null || stageShadowPasses <= 0
                    || com.cappleapple.openlights.config.ClientConfig.MAX_SHADOW_LIGHTS.get() != 1
                    || OpenLightRenderer.statistics().lights() != (resized ? 1 : 2)
                    || !shadowADefinition.equals(OpenLightRenderer.frameLights().get(shadowA.key()))
                    || targetShadowResolution() != (stage == Stage.SHADOW_RESIZE_128 ? 128 : 256)) return false;
            if (stage == Stage.SHADOW_A_RESELECTED && record == firstShadowARecord) return false;
            return !resized || record != preResizeShadowRecord;
        }
        if (stage == Stage.NATIVE_HANDHELD_ON || stage == Stage.NATIVE_HANDHELD_OFF) {
            ItemStack held = minecraft.player.getMainHandItem();
            LightKey key = new LightKey(new ResourceLocation("openlights","handheld"), player);
            boolean on = stage == Stage.NATIVE_HANDHELD_ON;
            return held.is(ModContent.FLASHLIGHT.get()) && FlashlightItem.isEnabled(held) == on
                    && (on ? OpenLightRenderer.frameLights().size() == 1
                            && OpenLightRenderer.frameLights().get(key) instanceof LightDefinition.Spot
                            && stageShadowPasses > 0
                            : OpenLightRenderer.frameLights().isEmpty() && OpenLightRenderer.statistics().lights() == 0);
        }
        if (stage == Stage.NATIVE_BLOCKS_ON || stage == Stage.NATIVE_BLOCKS_DYED_RANGE
                || stage == Stage.NATIVE_BLOCKS_OFF || stage == Stage.WATER_ICE_MEDIA) {
            boolean on = stage != Stage.NATIVE_BLOCKS_OFF;
            boolean edited = stage != Stage.NATIVE_BLOCKS_ON;
            if (!nativeBlocksMatch(on, edited)) return false;
            if (stage == Stage.WATER_ICE_MEDIA) {
                var media = cachedSceneSnapshot().media();
                Vec3 waterInterior = Vec3.atLowerCornerOf(WATER_POSITION).add(.5,.25,.5);
                return minecraft.level.getBlockState(WATER_POSITION).is(Blocks.WATER)
                        && minecraft.level.getBlockState(ICE_POSITION).is(Blocks.ICE)
                        && media.stream().anyMatch(m -> m.bounds().contains(waterInterior)
                            && Math.abs(m.throughput() - .98F) < .0001F
                            && m.bounds().maxY > WATER_POSITION.getY() + .8
                            && m.bounds().maxY < WATER_POSITION.getY() + 1)
                        && media.stream().anyMatch(m -> m.bounds().contains(Vec3.atCenterOf(ICE_POSITION))
                            && Math.abs(m.throughput() - .90F) < .0001F)
                        && OpenLightRenderer.statistics().media() >= 2;
            }
            return true;
        }
        if (stage == Stage.CONTENT_CLEANUP) {
            return minecraft.player.getMainHandItem().isEmpty() && OpenLightRenderer.frameLights().isEmpty()
                    && OpenLightRenderer.statistics().lights() == 0
                    && NATIVE_POSITIONS.stream().allMatch(pos -> minecraft.level.getBlockState(pos).isAir())
                    && WATER_CONTAINER.stream().allMatch(pos -> minecraft.level.getBlockState(pos).isAir())
                    && minecraft.level.getBlockState(WATER_POSITION).isAir()
                    && minecraft.level.getBlockState(ICE_POSITION).isAir();
        }
        if (stage == Stage.PERFORMANCE_STATIC || stage == Stage.PERFORMANCE_MOVING) {
            return performanceLights.size() == 8 && OpenLightRenderer.frameLights().size() == 8
                    && OpenLightRenderer.statistics().lights() == 8
                    && performanceLights.stream().allMatch(handle -> OpenLightRenderer.frameLights().containsKey(handle.key()))
                    && com.cappleapple.openlights.config.ClientConfig.MAX_SHADOW_LIGHTS.get() == 4;
        }
        if (stage == Stage.OCCLUDER_ADDED || stage == Stage.OCCLUDER_REMOVED) {
            boolean adding = stage == Stage.OCCLUDER_ADDED;
            return minecraft.level.getBlockState(OCCLUDER).is(adding ? Blocks.STONE : Blocks.AIR)
                    && uploadedSceneRevision() > occluderRevision && stageShadowPasses > 0
                    && OpenLightRenderer.frameLights().containsKey(apiLight.key())
                    && (adding ? OpenLightRenderer.statistics().triangles() >= trianglesWithoutOccluder + 12
                        : OpenLightRenderer.statistics().triangles() == trianglesWithoutOccluder);
        }
        if (stage == Stage.API_CLOSED) {
            return !apiLight.isValid() && !OpenLightRenderer.frameLights().containsKey(apiLight.key())
                    && OpenLightRenderer.statistics().lights() == 0;
        }
        if (stage == Stage.API_POINT || stage == Stage.API_SPOT || stage == Stage.API_AREA) {
            LightDefinition light = OpenLightRenderer.frameLights().get(apiLight.key());
            boolean typeMatches = stage == Stage.API_POINT ? light instanceof LightDefinition.Point
                    : stage == Stage.API_SPOT ? light instanceof LightDefinition.Spot
                    : light instanceof LightDefinition.Area;
            return apiLight.isValid() && typeMatches && light.position().equals(API_SOURCE)
                    && minecraft.level.getBlockState(new BlockPos(0,162,3)).is(Blocks.RED_STAINED_GLASS)
                    && OpenLightRenderer.statistics().lights() > 0
                    && OpenLightRenderer.statistics().triangles() > 0
                    && OpenLightRenderer.statistics().media() > 0 && stageShadowPasses > 0;
        }
        if (stage == Stage.EMPTY_HAND) {
            return minecraft.player.getMainHandItem().isEmpty() && !weaponBeam && !backendBeam;
        }
        if (stage == Stage.ATTACHMENT_REMOVED) {
            ItemStack held = minecraft.player.getMainHandItem();
            IGun item = IGun.getIGunOrNull(held);
            return item != null && id("glock_17").equals(item.getGunId(held))
                    && item.getAttachment(held, AttachmentType.LASER).isEmpty()
                    && item.getBuiltinAttachment(held, AttachmentType.LASER).isEmpty()
                    && !weaponBeam && !backendBeam;
        }
        String gun = switch (stage) {
            case PEQ6_ON -> "hk_mk23";
            case NIGHTSTICK_ON, HIDDEN_GUI, THIRD_PERSON -> "glock_17";
            default -> "m4a1";
        };
        String attachment = switch (stage) {
            case PEQ6_ON -> "laser_peq6";
            case NIGHTSTICK_ON, HIDDEN_GUI, THIRD_PERSON -> "laser_nightstick";
            default -> "laser_peq15";
        };
        ItemStack held = minecraft.player.getMainHandItem();
        IGun item = IGun.getIGunOrNull(held);
        if (item == null || !id(gun).equals(item.getGunId(held))
                || !id(attachment).equals(item.getAttachmentId(held, AttachmentType.LASER))) return false;
        if (stage == Stage.PEQ15_OFF) {
            return !WeaponLightManager.isEnabled(player) && !weaponBeam && !backendBeam;
        }
        boolean expectedFirstPerson = stage != Stage.THIRD_PERSON;
        return WeaponLightManager.isEnabled(player) && weaponBeam && backendBeam
                && minecraft.options.getCameraType().isFirstPerson() == expectedFirstPerson
                && (stage == Stage.HIDDEN_GUI ? minecraft.options.hideGui && capturedPose(player) == null
                    : capturedPoseMatches(player, expectedFirstPerson))
                && OpenLightRenderer.statistics().lights() > 0
                && OpenLightRenderer.statistics().triangles() > 0
                && stageShadowPasses > 0;
    }

    private static boolean profilesSynchronized() {
        var server = BeamProfiles.serverSnapshot();
        return BeamProfiles.clientRevision() == server.revision()
                && BeamProfiles.clientSnapshot().equals(server.profiles())
                && BeamProfiles.clientDefaults().equals(server.defaults());
    }

    private static BeamProfile expectedProfile(boolean reloaded) {
        var defaults = BeamProfiles.clientDefaults();
        return new BeamProfile(
                new BeamProfile.Layer(reloaded ? new Vec3(0,1,0) : new Vec3(1,0,0),
                        reloaded ? 1.25F : 2F, reloaded ? 18F : 12F, reloaded ? 14F : 18F,
                        reloaded ? .35F : .2F, reloaded ? 1.5F : 2F),
                new BeamProfile.Layer(new Vec3(0,0,1), reloaded ? .4F : .7F,
                        reloaded ? 55F : 50F, reloaded ? 22F : 24F,
                        defaults.outer().edgeSoftness(), defaults.outer().falloff()),
                reloaded ? .15F : .6F,
                new BeamProfile.Dust(reloaded ? 12F : 20F, defaults.dust().size(),
                        defaults.dust().lifetimeTicks(), defaults.dust().speed()), true);
    }

    private static boolean loproCondition(UUID player) {
        Minecraft minecraft = Minecraft.getInstance();
        ItemStack held = minecraft.player.getMainHandItem();
        IGun item = IGun.getIGunOrNull(held);
        boolean firstPerson = stage != Stage.LOPRO_THIRD_PERSON;
        if (item == null || !id("m4a1").equals(item.getGunId(held))
                || !LOPRO.equals(item.getAttachmentId(held, AttachmentType.LASER))
                || !WeaponLightManager.isEnabled(player)
                || minecraft.options.getCameraType().isFirstPerson() != firstPerson
                || !capturedPoseMatches(player, firstPerson) || !profilesSynchronized()) return false;
        var beam = WeaponLightManager.activeLights().stream().filter(value -> value.id().equals(player)).findFirst();
        if (beam.isEmpty() || !beam.get().attachmentId().equals(LOPRO)
                || beam.get().worldDirection().dot(minecraft.player.getLookAngle()) <= .9
                || beam.get().worldOrigin().distanceTo(minecraft.player.getEyePosition()) >= 2) return false;
        LightDefinition light = OpenLightRenderer.frameLights().get(
                new LightKey(new ResourceLocation("taczopenlights", "weapon"), player));
        if (!(light instanceof LightDefinition.Spot spot)
                || spot.forward().dot(minecraft.player.getLookAngle()) <= .9) return false;
        if (stage == Stage.PROFILE_REMOVED) {
            return BeamProfiles.clientRevision() > profileRevision
                    && !BeamProfiles.clientSnapshot().containsKey(LOPRO)
                    && BeamProfiles.clientProfile(LOPRO).equals(BeamProfiles.clientDefaults())
                    && BeamProfiles.clientDefaults().equals(spot.beamProfile())
                    && !Files.exists(smokePackDirectory);
        }
        BeamProfile expected = expectedProfile(stage == Stage.PROFILE_RELOADED);
        return expected.equals(BeamProfiles.clientProfile(LOPRO)) && expected.equals(spot.beamProfile())
                && Math.abs(spot.volumetricStrength() - expected.fogDensity()) < .0001F
                && BeamDustParticles.activeCount() > 0 && stageShadowPasses > 0
                && (stage != Stage.PROFILE_RELOADED || BeamProfiles.clientRevision() > profileRevision);
    }

    private static void prepareDatapack() {
        Path world = Minecraft.getInstance().gameDirectory.toPath().toAbsolutePath().normalize()
                .resolve("saves").resolve(WORLD);
        if (!Files.isDirectory(world)) throw new IllegalStateException("Smoke world must already exist: " + world);
        smokePackDirectory = world.resolve("datapacks").resolve(SMOKE_PACK_NAME).normalize();
        if (!smokePackDirectory.startsWith(world)) throw new IllegalStateException("Unsafe smoke datapack path");
        try {
            Files.createDirectories(smokeProfileFile().getParent());
            Files.writeString(smokePackDirectory.resolve("pack.mcmeta"),
                    "{\"pack\":{\"pack_format\":15,\"description\":\"Disposable Open Lights client smoke fixture\"}}",
                    StandardCharsets.UTF_8);
            writeSmokeProfile(false);
            LOGGER.info("SMOKE_DATAPACK created {} before login", smokePackDirectory);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot create disposable smoke datapack", exception);
        }
    }

    private static Path smokeProfileFile() {
        return smokePackDirectory.resolve("data/tacz/openlights/beam_profiles/laser_lopro.json");
    }

    private static void writeSmokeProfile(boolean reloaded) throws IOException {
        String profile = reloaded ? """
                {"inner":{"color":"#00FF00","angleDegrees":18,"intensity":1.25,"range":14,
                  "edgeSoftness":0.35,"falloff":1.5},
                 "outer":{"color":"#0000FF","angleDegrees":55,"intensity":0.4,"range":22},
                 "fogDensity":0.15,"dust":{"rate":12},"shadows":true}
                """ : """
                {"inner":{"color":"#FF0000","angleDegrees":12,"intensity":2,"range":18,
                  "edgeSoftness":0.2,"falloff":2},
                 "outer":{"color":"#0000FF","angleDegrees":50,"intensity":0.7,"range":24},
                 "fogDensity":0.6,"dust":{"rate":20},"shadows":true}
                """;
        Files.writeString(smokeProfileFile(), profile, StandardCharsets.UTF_8);
    }

    private static void reloadProfile(boolean changed) {
        var server = Minecraft.getInstance().getSingleplayerServer();
        fixtureUpdate = new CompletableFuture<>();
        CompletableFuture<Void> completion = fixtureUpdate;
        server.execute(() -> {
            try {
                if (changed) writeSmokeProfile(true);
                server.getPackRepository().reload();
                List<String> selected = new ArrayList<>(server.getPackRepository().getSelectedIds());
                String packId = "file/" + SMOKE_PACK_NAME;
                selected.remove(packId);
                if (changed) selected.add(packId);
                server.reloadResources(selected).whenComplete((ignored, throwable) -> {
                    if (throwable != null) completion.completeExceptionally(throwable);
                    else {
                        try {
                            if (!changed) removeSmokePack();
                            completion.complete(null);
                        } catch (IOException exception) {
                            completion.completeExceptionally(exception);
                        }
                    }
                });
            } catch (Throwable throwable) {
                completion.completeExceptionally(throwable);
            }
        });
    }

    private static void removeSmokePack() throws IOException {
        // Delete only the exact files/directories created by this run; never recursively delete a world path.
        Files.deleteIfExists(smokeProfileFile());
        Files.deleteIfExists(smokeProfileFile().getParent());
        Files.deleteIfExists(smokePackDirectory.resolve("data/tacz/openlights"));
        Files.deleteIfExists(smokePackDirectory.resolve("data/tacz"));
        Files.deleteIfExists(smokePackDirectory.resolve("data"));
        Files.deleteIfExists(smokePackDirectory.resolve("pack.mcmeta"));
        Files.deleteIfExists(smokePackDirectory);
        LOGGER.info("SMOKE_DATAPACK removed {}; server defaults restored by reload", smokePackDirectory);
    }

    private static LightDefinition.Spot grazingLight(boolean shadows) {
        return new LightDefinition.Spot(new Vec3(.5,162.6,.5), new Vec3(0,-.05,1), new Vec3(0,1,0),
                new Vec3(1,1,1), 12F, 24, 46, 30, shadows, 0);
    }

    private static void prepareGrazingScene() {
        contentAction(player -> {
            clearNativeContent(player);
            var level = player.serverLevel();
            for (int x = -8; x <= 8; x++) {
                for (int z = -6; z <= 25; z++) {
                    level.setBlock(new BlockPos(x,160,z), Blocks.STONE.defaultBlockState(), 3);
                }
                for (int y = 161; y <= 166; y++) {
                    level.setBlock(new BlockPos(x,y,8), Blocks.AIR.defaultBlockState(), 3);
                    level.setBlock(new BlockPos(x,y,25), Blocks.STONE_BRICKS.defaultBlockState(), 3);
                }
            }
            for (int y = 161; y <= 162; y++) {
                level.setBlock(new BlockPos(-2,y,9), Blocks.STONE_BRICKS.defaultBlockState(), 3);
            }
            level.setBlock(new BlockPos(2,161,14), Blocks.STONE_BRICKS.defaultBlockState(), 3);
            // The central cube casts a shadow through the bright core, roughly z=8 to z=20.
            level.setBlock(new BlockPos(0,161,7), Blocks.STONE_BRICKS.defaultBlockState(), 3);
            // Observe from above and to one side so the cube does not hide its own floor shadow.
            for (int x = 3; x <= 5; x++) for (int z = -4; z <= -2; z++) {
                level.setBlock(new BlockPos(x,161,z), Blocks.STONE.defaultBlockState(), 3);
            }
            player.teleportTo(level, 4.5,162,-2.5,16,15);
        });
    }

    private static void prepareWorld() {
        Minecraft minecraft = Minecraft.getInstance();
        UUID playerId = minecraft.player.getUUID();
        var server = minecraft.getSingleplayerServer();
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) return;
            var level = player.serverLevel();
            level.setDayTime(18000);
            level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
            for (int x = -6; x <= 6; x++) {
                for (int z = -6; z <= 10; z++) {
                    level.setBlock(new BlockPos(x, 160, z), Blocks.STONE.defaultBlockState(), 3);
                }
                for (int y = 161; y <= 166; y++) {
                    level.setBlock(new BlockPos(x, y, 8), Blocks.STONE_BRICKS.defaultBlockState(), 3);
                }
            }
            clearNativeContent(player);
            level.setBlock(new BlockPos(0,161,7), Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(new BlockPos(2,161,14), Blocks.AIR.defaultBlockState(), 3);
            for (int y = 161; y <= 162; y++) {
                level.setBlock(new BlockPos(-2,y,9), Blocks.AIR.defaultBlockState(), 3);
            }
            for (int x = 3; x <= 5; x++) for (int z = -4; z <= -2; z++) {
                level.setBlock(new BlockPos(x,161,z), Blocks.AIR.defaultBlockState(), 3);
            }
            level.setBlock(new BlockPos(0,162,3), Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(OCCLUDER, Blocks.AIR.defaultBlockState(), 3);
            player.setGameMode(GameType.CREATIVE);
            player.teleportTo(level, 0.5, 161, 0.5, 0.0F, 0.0F);
        });
    }

    private static void prepareApiSource() {
        var server = Minecraft.getInstance().getSingleplayerServer();
        fixtureUpdate = new CompletableFuture<>();
        CompletableFuture<Void> completion = fixtureUpdate;
        server.execute(() -> {
            try {
                server.overworld().setBlock(new BlockPos(0,162,3), Blocks.RED_STAINED_GLASS.defaultBlockState(), 3);
                completion.complete(null);
            } catch (Throwable throwable) {
                completion.completeExceptionally(throwable);
            }
        });
    }

    private static void createShadowRegressionLights() {
        com.cappleapple.openlights.config.ClientConfig.MAX_SHADOW_LIGHTS.set(1);
        com.cappleapple.openlights.config.ClientConfig.SHADOW_RESOLUTION.set(256);
        ResourceLocation owner = new ResourceLocation("openlights", "smoke_shadow_regression");
        shadowADefinition = shadowRegressionDefinition(new Vec3(.5,162.5,3.5));
        shadowA = OpenLightsApi.create(owner, shadowADefinition);
        shadowB = OpenLightsApi.create(owner, shadowRegressionDefinition(new Vec3(4.5,162.5,5.5)));
    }

    private static LightDefinition shadowRegressionDefinition(Vec3 position) {
        return new LightDefinition.Spot(position, new Vec3(0,0,1), new Vec3(0,1,0),
                new Vec3(1,1,1), 1, 16, 55, 35, true, .2F);
    }

    private static Map<?, ?> shadowRecords() {
        try {
            Field shadows = OpenLightRenderer.class.getDeclaredField("SHADOWS");
            shadows.setAccessible(true);
            return (Map<?, ?>) shadows.get(null);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Smoke shadow cache inspection failed", exception);
        }
    }

    private static int targetShadowResolution() {
        try {
            Field targetsField = OpenLightRenderer.class.getDeclaredField("TARGETS");
            targetsField.setAccessible(true);
            Object targets = targetsField.get(null);
            Field resolution = targets.getClass().getDeclaredField("resolution");
            resolution.setAccessible(true);
            return resolution.getInt(targets);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Smoke shadow target inspection failed", exception);
        }
    }

    private static void contentAction(Consumer<ServerPlayer> action) {
        Minecraft minecraft = Minecraft.getInstance();
        UUID playerId = minecraft.player.getUUID();
        var server = minecraft.getSingleplayerServer();
        CompletableFuture<Void> previous = fixtureUpdate;
        fixtureUpdate = new CompletableFuture<>();
        CompletableFuture<Void> completion = fixtureUpdate;
        server.execute(() -> {
            try {
                previous.join();
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player == null) throw new IllegalStateException("Smoke content player disappeared");
                action.accept(player);
                player.inventoryMenu.broadcastChanges();
                completion.complete(null);
            } catch (Throwable throwable) {
                completion.completeExceptionally(throwable);
            }
        });
    }

    private static void clearNativeContent(ServerPlayer player) {
        var level = player.serverLevel();
        level.setBlock(WATER_POSITION, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(ICE_POSITION, Blocks.AIR.defaultBlockState(), 3);
        WATER_CONTAINER.forEach(pos -> level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3));
        NATIVE_POSITIONS.forEach(pos -> level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3));
        player.setShiftKeyDown(false);
        player.getInventory().selected = 0;
        player.getInventory().setItem(0, ItemStack.EMPTY);
        player.getInventory().setItem(40, ItemStack.EMPTY);
    }

    private static void useNativeFlashlight(boolean create) {
        contentAction(player -> {
            if (create) {
                clearNativeContent(player);
                player.serverLevel().setBlock(new BlockPos(0,162,3), Blocks.AIR.defaultBlockState(), 3);
                player.getInventory().setItem(0, new ItemStack(ModContent.FLASHLIGHT.get()));
            }
            ItemStack stack = player.getMainHandItem();
            if (!stack.is(ModContent.FLASHLIGHT.get())) throw new IllegalStateException("Native flashlight fixture missing");
            boolean before = FlashlightItem.isEnabled(stack);
            var result = stack.getItem().use(player.serverLevel(), player, InteractionHand.MAIN_HAND);
            if (!result.getResult().consumesAction() || FlashlightItem.isEnabled(stack) == before) {
                throw new IllegalStateException("Native flashlight use did not toggle its server-owned state");
            }
        });
    }

    private static List<LightSourceBlock> nativeBlocks() {
        return List.of(ModContent.POINT_LIGHT.get(), ModContent.SPOT_LIGHT.get(), ModContent.AREA_LIGHT.get());
    }

    private static void placeNativeBlocks() {
        contentAction(player -> {
            clearNativeContent(player);
            List<LightSourceBlock> blocks = nativeBlocks();
            for (int i = 0; i < blocks.size(); i++) {
                player.serverLevel().setBlock(NATIVE_POSITIONS.get(i),
                        blocks.get(i).defaultBlockState().setValue(LightSourceBlock.FACING, Direction.SOUTH), 3);
                if (!(player.serverLevel().getBlockEntity(NATIVE_POSITIONS.get(i)) instanceof LightSourceBlockEntity)) {
                    throw new IllegalStateException("Native light block entity was not created");
                }
            }
        });
    }

    private static void interactNativeBlocks(boolean dye, boolean range) {
        contentAction(player -> {
            List<LightSourceBlock> blocks = nativeBlocks();
            for (int i = 0; i < blocks.size(); i++) {
                BlockPos pos = NATIVE_POSITIONS.get(i);
                var state = player.serverLevel().getBlockState(pos);
                BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.NORTH, pos, false);
                player.getInventory().setItem(0, dye ? new ItemStack(Items.RED_DYE) : ItemStack.EMPTY);
                player.setShiftKeyDown(false);
                if (!blocks.get(i).use(state, player.serverLevel(), pos, player, InteractionHand.MAIN_HAND, hit).consumesAction()) {
                    throw new IllegalStateException("Native light interaction was not consumed");
                }
                if (range) {
                    player.getInventory().setItem(0, ItemStack.EMPTY);
                    player.setShiftKeyDown(true);
                    if (!blocks.get(i).use(state, player.serverLevel(), pos, player, InteractionHand.MAIN_HAND, hit).consumesAction()) {
                        throw new IllegalStateException("Native light range interaction was not consumed");
                    }
                }
            }
            player.setShiftKeyDown(false);
            player.getInventory().setItem(0, ItemStack.EMPTY);
        });
    }

    private static boolean nativeBlocksMatch(boolean enabled, boolean edited) {
        var minecraft = Minecraft.getInstance();
        var frame = OpenLightRenderer.frameLights();
        if (frame.size() != (enabled ? 3 : 0) || OpenLightRenderer.statistics().lights() != (enabled ? 3 : 0)) return false;
        List<LightSourceBlock> blocks = nativeBlocks();
        int expectedColor = edited ? DyeColor.RED.getTextColor() : 0xffffff;
        float expectedRange = edited ? 32 : 24;
        Vec3 color = new Vec3(((expectedColor >> 16) & 255) / 255.0,
                ((expectedColor >> 8) & 255) / 255.0, (expectedColor & 255) / 255.0);
        for (int i = 0; i < blocks.size(); i++) {
            BlockPos pos = NATIVE_POSITIONS.get(i);
            if (!minecraft.level.getBlockState(pos).is(blocks.get(i))
                    || !(minecraft.level.getBlockEntity(pos) instanceof LightSourceBlockEntity block)
                    || block.enabled() != enabled || block.color() != expectedColor || block.range() != expectedRange) return false;
            LightShape shape = blocks.get(i).shape();
            LightKey key = new LightKey(new ResourceLocation("openlights","block"), new UUID(pos.asLong(), shape.ordinal()));
            LightDefinition light = frame.get(key);
            if (!enabled) {
                if (light != null) return false;
                continue;
            }
            boolean typeMatches = shape == LightShape.POINT ? light instanceof LightDefinition.Point
                    : shape == LightShape.SPOT ? light instanceof LightDefinition.Spot : light instanceof LightDefinition.Area;
            if (!typeMatches || !light.color().equals(color)
                    || light.range() != Math.min(expectedRange, com.cappleapple.openlights.config.ClientConfig.MAX_RANGE.get())) return false;
        }
        return !enabled || stageShadowPasses > 0;
    }

    private static void prepareWaterIce() {
        // Queued after the block-toggle action on the same integrated-server executor.
        contentAction(player -> {
            var level = player.serverLevel();
            WATER_CONTAINER.forEach(pos -> level.setBlock(pos,
                    (pos.equals(WATER_POSITION.below()) ? Blocks.STONE : Blocks.GLASS).defaultBlockState(), 3));
            level.setBlock(WATER_POSITION, Blocks.WATER.defaultBlockState(), 3);
            level.setBlock(ICE_POSITION, Blocks.ICE.defaultBlockState(), 3);
        });
    }

    private static SceneSnapshot cachedSceneSnapshot() {
        try {
            Field cacheField = OpenLightRenderer.class.getDeclaredField("SCENE");
            cacheField.setAccessible(true);
            Field snapshotField = SceneCache.class.getDeclaredField("snapshot");
            snapshotField.setAccessible(true);
            return (SceneSnapshot) snapshotField.get(cacheField.get(null));
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Smoke medium cache inspection failed", exception);
        }
    }

    private static void setOccluder(boolean present) {
        var server = Minecraft.getInstance().getSingleplayerServer();
        fixtureUpdate = new CompletableFuture<>();
        CompletableFuture<Void> completion = fixtureUpdate;
        server.execute(() -> {
            try {
                server.overworld().setBlock(OCCLUDER,
                        (present ? Blocks.STONE : Blocks.AIR).defaultBlockState(), 3);
                completion.complete(null);
            } catch (Throwable throwable) {
                completion.completeExceptionally(throwable);
            }
        });
    }

    private static void createPerformanceLights() {
        com.cappleapple.openlights.config.ClientConfig.MAX_LIGHTS.set(8);
        com.cappleapple.openlights.config.ClientConfig.MAX_SHADOW_LIGHTS.set(4);
        resetPerformanceSamples();
        for (int i = 0; i < 8; i++) {
            performanceLights.add(OpenLightsApi.create(new ResourceLocation("openlights", "smoke_performance"),
                    performanceDefinition(i, 0)));
        }
        LOGGER.info("SMOKE_PERFORMANCE_CONTEXT gpu={} vendor={} gl={} window={}x{} renderScale={} "
                        + "shadowResolution={} volumetricSteps={} maxShadowLights=4 fixture=3point_3spot_2area "
                        + "range=16 rendererTimingOnly=true",
                GL11.glGetString(GL11.GL_RENDERER), GL11.glGetString(GL11.GL_VENDOR), GL11.glGetString(GL11.GL_VERSION),
                Minecraft.getInstance().getMainRenderTarget().width, Minecraft.getInstance().getMainRenderTarget().height,
                com.cappleapple.openlights.config.ClientConfig.RENDER_SCALE.get(),
                com.cappleapple.openlights.config.ClientConfig.SHADOW_RESOLUTION.get(),
                com.cappleapple.openlights.config.ClientConfig.VOLUMETRIC_STEPS.get());
    }

    private static LightDefinition performanceDefinition(int index, double movement) {
        Vec3 position = new Vec3(-2.5 + (index % 4) * 2 + movement,
                162.2 + (index / 4) * 1.4, 4.5 + movement * .4);
        Vec3 color = new Vec3(1, .88, .72), forward = new Vec3(0,0,1), up = new Vec3(0,1,0);
        return switch (index % 3) {
            case 0 -> new LightDefinition.Point(position, color, .35F, 16, true, .15F);
            case 1 -> new LightDefinition.Spot(position, forward, up, color, .35F, 16, 65, 40, true, .15F);
            default -> new LightDefinition.Area(position, forward, up, color, .35F, 16, 1.5F, 1, 75, true, .15F);
        };
    }

    private static void movePerformanceLights() {
        double movement = Math.sin(++movingFrame * .07) * .2;
        for (int i = 0; i < performanceLights.size(); i++) {
            performanceLights.get(i).update(performanceDefinition(i, movement));
        }
    }

    private static void resetPerformanceSamples() {
        cpuSamples.clear();
        gpuSamples.clear();
        shadowSamples.clear();
        movingFrame = 0;
    }

    private static boolean samplePerformance() {
        if (secondsSince(stageStarted) < 2) return false;
        var stats = OpenLightRenderer.statistics();
        if (!Double.isFinite(stats.cpuMillis()) || !Double.isFinite(stats.gpuMillis())
                || stats.cpuMillis() <= 0 || stats.gpuMillis() <= 0) return false;
        cpuSamples.add(stats.cpuMillis());
        gpuSamples.add(stats.gpuMillis());
        shadowSamples.add((double) stats.shadowPasses());
        if (cpuSamples.size() < PERFORMANCE_FRAMES) return false;
        LOGGER.info(String.format(Locale.ROOT,
                "SMOKE_PERFORMANCE workload=%s frames=%d lights=8 maxShadowLights=4 "
                        + "cpuMeanMs=%.4f cpuP95Ms=%.4f gpuMeanMs=%.4f gpuP95Ms=%.4f "
                        + "shadowPassesMean=%.3f sceneTriangles=%d media=%d timingScope=OpenLightsRenderer",
                stage == Stage.PERFORMANCE_STATIC ? "stationary" : "moving",
                cpuSamples.size(), mean(cpuSamples), p95(cpuSamples), mean(gpuSamples), p95(gpuSamples),
                mean(shadowSamples), stats.triangles(), stats.media()));
        return true;
    }

    private static double mean(List<Double> samples) {
        return samples.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
    }

    private static double p95(List<Double> samples) {
        List<Double> sorted = new ArrayList<>(samples);
        sorted.sort(Double::compare);
        return sorted.get((int)Math.ceil(sorted.size() * .95) - 1);
    }

    private static long uploadedSceneRevision() {
        try {
            Field revision = OpenLightRenderer.class.getDeclaredField("uploadedRevision");
            revision.setAccessible(true);
            return revision.getLong(null);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Smoke scene revision inspection failed", exception);
        }
    }

    private static void equip(String gunId, String attachmentId) {
        Minecraft minecraft = Minecraft.getInstance();
        UUID playerId = minecraft.player.getUUID();
        minecraft.player.getInventory().selected = 0;
        var server = minecraft.getSingleplayerServer();
        fixtureUpdate = new CompletableFuture<>();
        CompletableFuture<Void> completion = fixtureUpdate;
        server.execute(() -> {
            try {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player == null) throw new IllegalStateException("Smoke player is absent on the server");
                ItemStack gun = ItemStack.EMPTY;
                if (gunId != null) {
                    GunItemBuilder builder = GunItemBuilder.create().setId(id(gunId));
                    if (attachmentId != null) builder.putAttachment(AttachmentType.LASER, id(attachmentId));
                    gun = builder.build();
                }
                if (gunId != null && gun.isEmpty()) {
                    throw new IllegalStateException("TACZ gun fixture was not found: " + gunId);
                }
                if (attachmentId != null && !id(attachmentId).equals(
                        IGun.getIGunOrNull(gun).getAttachmentId(gun, AttachmentType.LASER))) {
                    throw new IllegalStateException("TACZ attachment fixture was not installed: " + attachmentId);
                }
                player.getInventory().selected = 0;
                player.getInventory().setItem(0, gun);
                player.getInventory().setItem(40, ItemStack.EMPTY);
                player.inventoryMenu.broadcastChanges();
                completion.complete(null);
            } catch (Throwable throwable) {
                completion.completeExceptionally(throwable);
            }
        });
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation("tacz", path);
    }

    private static void begin(Stage next) {
        stage = next;
        stageTicks = 0;
        stableFrames = 0;
        stageShadowPasses = 0;
        stageStarted = System.nanoTime();
        LOGGER.info("SMOKE_STAGE {}", next);
    }

    private static String diagnostics() {
        Minecraft minecraft = Minecraft.getInstance();
        return "stage=" + stage + ", camera=" + minecraft.options.getCameraType()
                + ", held=" + (minecraft.player == null ? "no player" : minecraft.player.getMainHandItem())
                + ", enabled=" + (minecraft.player != null && WeaponLightManager.isEnabled(minecraft.player.getUUID()))
                + ", beams=" + WeaponLightManager.activeLights().size()
                + ", backend=" + OpenLightRenderer.frameLights().keySet()
                + ", renderer=" + OpenLightRenderer.statistics()
                + ", stageShadowPasses=" + stageShadowPasses
                + ", capturedPose=" + (minecraft.player != null && capturedPose(minecraft.player.getUUID()) != null)
                + ", apiLight=" + (apiLight == null ? "none" : apiLight.key())
                + ", shadowOwners=" + shadowRecords().keySet() + ", shadowResolution=" + targetShadowResolution()
                + ", profileRevision=" + BeamProfiles.clientRevision() + ", profileIds=" + BeamProfiles.clientSnapshot().keySet()
                + ", dust=" + BeamDustParticles.activeCount()
                + ", loproDirectionDot=" + (minecraft.player == null ? 0 : WeaponLightManager.activeLights().stream()
                    .filter(beam -> beam.attachmentId().equals(LOPRO))
                    .mapToDouble(beam -> beam.worldDirection().dot(minecraft.player.getLookAngle())).findFirst().orElse(0));
    }

    private static double secondsSince(long time) {
        return (System.nanoTime() - time) / 1_000_000_000.0;
    }

    private static boolean rendererFailed() {
        try {
            Field failed = OpenLightRenderer.class.getDeclaredField("failed");
            failed.setAccessible(true);
            return failed.getBoolean(null);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Smoke renderer status inspection failed", exception);
        }
    }

    private static Object capturedPose(UUID player) {
        try {
            Field poses = WeaponLightManager.class.getDeclaredField("POSES");
            poses.setAccessible(true);
            return ((Map<?, ?>) poses.get(null)).get(player);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Smoke capture inspection failed", exception);
        }
    }

    private static boolean capturedPoseMatches(UUID player, boolean firstPerson) {
        Object pose = capturedPose(player);
        if (pose == null) return false;
        try {
            var cameraMode = pose.getClass().getDeclaredMethod("firstPerson");
            cameraMode.setAccessible(true);
            var capturedFrame = pose.getClass().getDeclaredMethod("frame");
            capturedFrame.setAccessible(true);
            Field currentFrame = WeaponLightManager.class.getDeclaredField("frame");
            currentFrame.setAccessible(true);
            long age = currentFrame.getLong(null) - (Long) capturedFrame.invoke(pose);
            return (Boolean) cameraMode.invoke(pose) == firstPerson && age >= 0 && age <= 1;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Smoke capture inspection failed", exception);
        }
    }

    private static void hideWindow(Minecraft minecraft) {
        long window = minecraft.getWindow().getWindow();
        GLFW.glfwSetWindowAttrib(window, GLFW.GLFW_FOCUS_ON_SHOW, GLFW.GLFW_FALSE);
        GLFW.glfwHideWindow(window);
        minecraft.options.pauseOnLostFocus = false;
        minecraft.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.0);
        minecraft.mouseHandler.releaseMouse();
    }

    @Mod.EventBusSubscriber(modid = "taczopenlights", value = Dist.CLIENT,
            bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent
        public static void setup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> hideWindow(Minecraft.getInstance()));
        }
    }

    private static void fail(String message) {
        fail(message, null);
    }

    private static void fail(String message, Throwable cause) {
        failed = true;
        LOGGER.error("SMOKE_FAILURE {}", message, cause);
        throw new IllegalStateException("SMOKE_FAILURE " + message, cause);
    }

    private enum Stage {
        WAIT_WORLD, PROFILE_LOGIN, PEQ15_ON, PEQ15_OFF, PEQ15_REENABLED, PEQ6_ON, NIGHTSTICK_ON,
        HIDDEN_GUI, THIRD_PERSON, ATTACHMENT_REMOVED, EMPTY_HAND,
        API_POINT, API_SPOT, API_AREA, OCCLUDER_ADDED, OCCLUDER_REMOVED, API_CLOSED,
        PERFORMANCE_STATIC, PERFORMANCE_MOVING, NATIVE_HANDHELD_ON, NATIVE_HANDHELD_OFF,
        NATIVE_BLOCKS_ON, NATIVE_BLOCKS_DYED_RANGE, NATIVE_BLOCKS_OFF, WATER_ICE_MEDIA, CONTENT_CLEANUP,
        SHADOW_A_SELECTED, SHADOW_B_SELECTED, SHADOW_A_RESELECTED, SHADOW_RESIZE_128, SHADOW_RESIZE_256,
        LOPRO_FIRST_PERSON, LOPRO_THIRD_PERSON, PROFILE_RELOADED, PROFILE_REMOVED,
        GRAZING_SHADOWS, GRAZING_UNSHADOWED, FINISHED
    }
}
