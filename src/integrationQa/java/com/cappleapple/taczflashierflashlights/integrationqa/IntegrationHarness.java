package com.cappleapple.taczflashierflashlights.integrationqa;

import com.cappleapple.openlights.api.client.LightDefinition;
import com.cappleapple.openlights.api.client.LightKey;
import com.cappleapple.openlights.beam.BeamProfiles;
import com.cappleapple.openlights.client.render.OpenLightRenderer;
import com.cappleapple.taczflashierflashlights.client.ClientEvents;
import com.cappleapple.taczflashierflashlights.client.WeaponLightManager;
import com.cappleapple.taczflashierflashlights.network.FlashlightNetwork;
import com.mojang.logging.LogUtils;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Packaged TaCZ provider checks against the Open Lights library with no optional addon. */
@Mod.EventBusSubscriber(modid = "taczopenlights_integration_qa", value = Dist.CLIENT)
public final class IntegrationHarness {
    private static final String WORLD = "TaczIntegrationSmoke";
    private static final ResourceLocation PEQ15 = id("tacz", "laser_peq15");
    private static final ResourceLocation LOPRO = id("tacz", "laser_lopro");
    private static final boolean MATERIAL_SCENE = Boolean.getBoolean("taczopenlights.materialQa");
    private static final long START = System.nanoTime();
    private static Stage stage = Stage.BOOT;
    private static long stageStarted = START, previousProfile;
    private static int stageTicks, stableFrames, bootTicks, shadowPasses;
    private static boolean loading, failed;
    private static boolean externalResultLogged;
    private static CompletableFuture<Void> serverWork = CompletableFuture.completedFuture(null);

    private IntegrationHarness() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || failed || stage == Stage.DONE) return;
        var mc = Minecraft.getInstance();
        mc.mouseHandler.releaseMouse();
        org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());
        mc.options.pauseOnLostFocus = false;
        mc.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.0);
        try {
            if (++bootTicks == 1 || bootTicks % 200 == 0) {
                LogUtils.getLogger().info("TACZ2_PROGRESS stage={} screen={} ticks={}", stage,
                        mc.screen == null ? "none" : mc.screen.getClass().getName(), stageTicks);
            }
            if (stage == Stage.BOOT) {
                if (!loading && mc.screen instanceof TitleScreen) {
                    loading = true;
                    mc.options.renderDistance().set(4);
                    mc.options.simulationDistance().set(5);
                    mc.options.framerateLimit().set(60);
                    if (MATERIAL_SCENE) mc.options.fov().set(65);
                    mc.options.hideGui = false;
                    mc.options.setCameraType(CameraType.FIRST_PERSON);
                    var rules = new GameRules();
                    rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
                    rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
                    mc.createWorldOpenFlows().createFreshLevel(WORLD,
                            new LevelSettings("Disposable TaCZ/Open Lights QA", GameType.CREATIVE, false,
                                    Difficulty.PEACEFUL, true, rules, WorldDataConfiguration.DEFAULT),
                            new WorldOptions(212L, false, false), registry -> registry
                                    .registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT)
                                    .value().createWorldDimensions());
                }
                if (mc.player != null && mc.screen == null && mc.getSingleplayerServer() != null) {
                    authoritative(player -> {
                        var level = player.serverLevel();
                        level.setDayTime(18000);
                        for (int x = -8; x <= 8; x++) for (int z = -8; z <= 16; z++) {
                            level.setBlock(new BlockPos(x, -60, z), Blocks.STONE.defaultBlockState(), 3);
                            for (int y = -59; y <= -51; y++) level.setBlock(new BlockPos(x, y, z),
                                    z == 16 ? Blocks.WHITE_CONCRETE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
                        }
                        if (MATERIAL_SCENE) {
                            var materials = new net.minecraft.world.level.block.Block[] {
                                    Blocks.RED_WOOL, Blocks.ORANGE_CONCRETE, Blocks.OAK_PLANKS,
                                    Blocks.STONE_BRICKS, Blocks.GREEN_WOOL, Blocks.BLUE_WOOL,
                                    Blocks.PURPLE_CONCRETE, Blocks.COBBLESTONE
                            };
                            for (int x = -8; x <= 8; x++) for (int y = -59; y <= -51; y++) {
                                int material = Math.floorMod(x + 8 + (y + 59) * 3, materials.length);
                                level.setBlock(new BlockPos(x, y, 6), materials[material].defaultBlockState(), 3);
                            }
                        }
                        player.teleportTo(level, .5, -59, .5, 0, MATERIAL_SCENE ? 0 : 7);
                    });
                    if (MATERIAL_SCENE) writeMaterialScene();
                    begin(Stage.IDLE);
                } else if (seconds(START) > 180) throw new IllegalStateException("World startup timeout");
                return;
            }
            stageTicks++;
            if (serverWork.isDone()) serverWork.join();
            if (seconds(stageStarted) > 90) throw new IllegalStateException("Stage timeout " + diagnostics());
        } catch (Throwable error) { fail(error); }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void rendered(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || failed || stage == Stage.BOOT || stage == Stage.DONE) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.screen != null) return;
        try {
            shadowPasses += OpenLightRenderer.statistics().shadowPasses();
            requireNoOptionalWork();
            if (stageTicks < 60 || !serverWork.isDone() || !condition()) { stableFrames = 0; return; }
            if (++stableFrames < 8) return;
            LogUtils.getLogger().info("TACZ2_PASS {} {}", stage, diagnostics());
            Screenshot.grab(mc.gameDirectory, "tacz2-" + stage.name().toLowerCase(java.util.Locale.ROOT) + ".png",
                    mc.getMainRenderTarget(), message -> {});
            switch (stage) {
                case IDLE -> { equip(PEQ15); begin(Stage.PEQ15_ON); }
                case PEQ15_ON -> { KeyMapping.click(ClientEvents.TOGGLE.getKey()); begin(Stage.KEY_OFF); }
                case KEY_OFF -> { KeyMapping.click(ClientEvents.TOGGLE.getKey()); begin(Stage.KEY_ON); }
                case KEY_ON -> { equip(LOPRO); begin(Stage.LOPRO_ON); }
                case LOPRO_ON -> { mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); begin(Stage.THIRD_PERSON); }
                case THIRD_PERSON -> {
                    mc.options.setCameraType(CameraType.FIRST_PERSON);
                    previousProfile = BeamProfiles.clientRevision();
                    reloadProfile(); begin(Stage.PROFILE_RELOAD);
                }
                case PROFILE_RELOAD -> { serverWork = mc.reloadResourcePacks(); begin(Stage.RESOURCE_RELOAD); }
                case RESOURCE_RELOAD -> { equip(null); begin(Stage.ATTACHMENT_REMOVED); }
                case ATTACHMENT_REMOVED -> {
                    authoritative(player -> { player.getInventory().setItem(0, ItemStack.EMPTY); player.inventoryMenu.broadcastChanges(); });
                    begin(Stage.EMPTY_HAND);
                }
                case EMPTY_HAND -> {
                    require(FlashlightNetwork.serverPresent(), "Integrated server toggle relay absent");
                    stage = Stage.DONE;
                    LogUtils.getLogger().info("TACZ2_SUCCESS actual gun model capture, API rendering, key toggles, profiles, reload, removal, no GI");
                    mc.stop();
                }
                default -> throw new IllegalStateException("Unhandled stage " + stage);
            }
        } catch (Throwable error) { fail(error); }
    }

    private static boolean condition() throws Exception {
        var mc = Minecraft.getInstance();
        var key = new LightKey(id("taczopenlights", "weapon"), mc.player.getUUID());
        var light = OpenLightRenderer.frameLights().get(key);
        boolean enabled = WeaponLightManager.isEnabled(mc.player.getUUID());
        if (stage == Stage.IDLE || stage == Stage.ATTACHMENT_REMOVED || stage == Stage.EMPTY_HAND) {
            return light == null && WeaponLightManager.activeLights().isEmpty();
        }
        if (stage == Stage.KEY_OFF) return !enabled && light == null && WeaponLightManager.activeLights().isEmpty();
        if (!enabled || !(light instanceof LightDefinition.Spot spot) || OpenLightRenderer.statistics().lights() == 0
                || BeamProfiles.clientRevision() == 0 || !capturedPose(mc.player.getUUID(), stage != Stage.THIRD_PERSON)) return false;
        var expected = stage == Stage.PEQ15_ON || stage == Stage.KEY_ON ? PEQ15 : LOPRO;
        var beam = WeaponLightManager.activeLights().stream().filter(value -> value.id().equals(mc.player.getUUID())).findFirst().orElse(null);
        if (beam == null || !beam.attachmentId().equals(expected) || beam.worldDirection().dot(mc.player.getLookAngle()) < .85
                || spot.forward().dot(mc.player.getLookAngle()) < .85) return false;
        if (stage == Stage.PEQ15_ON && shadowPasses == 0) return false;
        if (stage == Stage.THIRD_PERSON && !externalPoseReady()) return false;
        if (stage == Stage.PROFILE_RELOAD || stage == Stage.RESOURCE_RELOAD) {
            return BeamProfiles.clientRevision() > previousProfile && spot.beamProfile() != null
                    && spot.beamProfile().equals(BeamProfiles.clientProfile(LOPRO))
                    && Math.abs(spot.beamProfile().inner().intensity() - 3.0) < .0001
                    && spot.beamProfile().inner().color().y > .99;
        }
        return true;
    }

    private static void requireNoOptionalWork() throws ClassNotFoundException {
        var cache = OpenLightRenderer.cacheStatistics();
        require(!OpenLightRenderer.hasWorldLightingExtension() && !cache.replacement() && cache.worldProbes() == 0
                && cache.giProbes() == 0 && cache.aggregateCells() == 0 && !cache.aggregateWorker(), "Unexpected GI/world cache activity: " + cache);
        require(!BuiltInRegistries.ITEM.containsKey(id("openlights", "flashlight")), "Handheld addon was loaded");
        try {
            Class.forName("com.cappleapple.openlights.gi.client.scene.WorldLightCache", false, IntegrationHarness.class.getClassLoader());
            throw new IllegalStateException("GI classes were loaded");
        } catch (ClassNotFoundException expected) {}
    }

    private static boolean capturedPose(UUID player, boolean firstPerson) throws Exception {
        var field = WeaponLightManager.class.getDeclaredField("POSES"); field.setAccessible(true);
        Object pose = ((Map<?, ?>) field.get(null)).get(player);
        if (pose == null) return false;
        var method = pose.getClass().getDeclaredMethod("firstPerson"); method.setAccessible(true);
        return ((boolean) method.invoke(pose)) == firstPerson;
    }

    private static boolean externalPoseReady() throws Exception {
        try {
            Class.forName("com.tacz.guns.client.renderer.item.ExternalGunItemVboRenderer", false,
                    IntegrationHarness.class.getClassLoader());
        } catch (ClassNotFoundException oldTacz) {
            return true;
        }
        Class<?> config = Class.forName("com.tacz.guns.config.client.RenderConfig");
        var enabled = (net.minecraftforge.common.ForgeConfigSpec.BooleanValue)
                config.getField("STATIC_ITEM_GUN_VBO").get(null);
        Class<?> mesh = Class.forName("com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.StaticMeshRenderer");
        boolean ownerEnabled = (boolean) mesh.getMethod("isOwnerEnabled", ResourceLocation.class)
                .invoke(null, id("tacz", "external_item_guns"));
        boolean supported = (boolean) mesh.getMethod("supportsInstanceAttributes", int.class, int.class)
                .invoke(null, 0xF000F0, 0xA0000);
        if (!enabled.get() || !ownerEnabled || !supported) {
            if (!externalResultLogged) {
                externalResultLogged = true;
                LogUtils.getLogger().info("TACZ2_EXTERNAL_VBO_UNAVAILABLE config={} owner={} attributes={}; checked real legacy third-person capture",
                        enabled.get(), ownerEnabled, supported);
            }
            return true;
        }
        Class<?> context = Class.forName("com.cappleapple.taczflashierflashlights.client.TaczExternalRenderContext");
        var unavailable = context.getDeclaredField("unavailable"); unavailable.setAccessible(true);
        require(!unavailable.getBoolean(null), "TaCZ 1.2 external pose bridge failed");
        var bridge = context.getDeclaredField("bridge"); bridge.setAccessible(true);
        if (bridge.get(null) == null) return false;
        if (!externalResultLogged) {
            externalResultLogged = true;
            LogUtils.getLogger().info("TACZ2_EXTERNAL_VBO_CAPTURE real external attachment pose with native static backend enabled");
        }
        return true;
    }

    private static void equip(ResourceLocation attachment) {
        authoritative(player -> {
            var builder = GunItemBuilder.create().setId(id("tacz", "m4a1"));
            if (attachment != null) builder.putAttachment(AttachmentType.LASER, attachment);
            var stack = builder.build();
            require(!stack.isEmpty(), "M4A1 fixture missing");
            if (attachment != null) require(attachment.equals(IGun.getIGunOrNull(stack).getAttachmentId(stack, AttachmentType.LASER)), "Attachment fixture missing");
            player.getInventory().selected = 0; player.getInventory().setItem(0, stack);
            player.getInventory().setItem(40, ItemStack.EMPTY); player.inventoryMenu.broadcastChanges();
        });
    }

    private static void reloadProfile() {
        var mc = Minecraft.getInstance();
        var server = mc.getSingleplayerServer();
        serverWork = new CompletableFuture<>();
        var result = serverWork;
        server.execute(() -> {
            try {
                Path pack = mc.gameDirectory.toPath().toAbsolutePath().resolve("saves/" + WORLD + "/datapacks/tacz2_profile");
                Path profile = pack.resolve("data/tacz/openlights/beam_profiles/laser_lopro.json");
                Files.createDirectories(profile.getParent());
                Files.writeString(pack.resolve("pack.mcmeta"), "{\"pack\":{\"pack_format\":15,\"description\":\"Disposable TaCZ2 QA profile\"}}", StandardCharsets.UTF_8);
                Files.writeString(profile, "{\"inner\":{\"color\":\"#00FF00\",\"intensity\":3},\"outer\":{\"color\":\"#0000FF\",\"intensity\":0.7}}", StandardCharsets.UTF_8);
                server.getPackRepository().reload();
                var selected = new ArrayList<>(server.getPackRepository().getSelectedIds());
                if (!selected.contains("file/tacz2_profile")) selected.add("file/tacz2_profile");
                server.reloadResources(selected).whenComplete((ignored, failure) -> {
                    if (failure == null) result.complete(null); else result.completeExceptionally(failure);
                });
            } catch (Throwable error) { result.completeExceptionally(error); }
        });
    }

    private static void authoritative(Consumer<ServerPlayer> action) {
        var mc = Minecraft.getInstance(); var server = mc.getSingleplayerServer(); var id = mc.player.getUUID();
        serverWork = new CompletableFuture<>(); var result = serverWork;
        server.execute(() -> {
            try {
                var player = server.getPlayerList().getPlayer(id);
                require(player != null, "Server fixture player absent"); action.accept(player); result.complete(null);
            } catch (Throwable error) { result.completeExceptionally(error); }
        });
    }

    private static void writeMaterialScene() throws java.io.IOException {
        Files.writeString(Minecraft.getInstance().gameDirectory.toPath().resolve("material-scene.json"), """
                {
                  "camera": {"player_x":0.5,"player_y":-59,"player_z":0.5,"eye_y":-57.38,"yaw":0,"pitch":0,"fov":65},
                  "wall": {"z":6,"min_x":-8,"max_x":8,"min_y":-59,"max_y":-51},
                  "palette": ["minecraft:red_wool","minecraft:orange_concrete","minecraft:oak_planks",
                              "minecraft:stone_bricks","minecraft:green_wool","minecraft:blue_wool",
                              "minecraft:purple_concrete","minecraft:cobblestone"],
                  "palette_index": "floorMod(x + 8 + (y + 59) * 3, 8)",
                  "white_beam_captures": ["tacz2-peq15_on.png","tacz2-key_on.png"],
                  "off_capture": "tacz2-key_off.png",
                  "colored_beam_capture": "tacz2-profile_reload.png",
                  "notes": "Compare identical fixed-camera captures across core artifacts; exclude weapon, HUD and native laser."
                }
                """, StandardCharsets.UTF_8);
    }

    private static void begin(Stage next) { stage = next; stageTicks = 0; stableFrames = 0; shadowPasses = 0; stageStarted = System.nanoTime(); }
    private static ResourceLocation id(String namespace, String path) { return new ResourceLocation(namespace, path); }
    private static double seconds(long start) { return (System.nanoTime() - start) / 1_000_000_000.0; }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
    private static String diagnostics() { return "ticks=" + stageTicks + " lights=" + OpenLightRenderer.frameLights() + " renderer=" + OpenLightRenderer.statistics() + " profile=" + BeamProfiles.clientRevision(); }
    private static void fail(Throwable error) { failed = true; LogUtils.getLogger().error("TACZ2_FAILURE stage=" + stage + " " + diagnostics(), error); Minecraft.getInstance().stop(); }
    private enum Stage { BOOT, IDLE, PEQ15_ON, KEY_OFF, KEY_ON, LOPRO_ON, THIRD_PERSON, PROFILE_RELOAD, RESOURCE_RELOAD, ATTACHMENT_REMOVED, EMPTY_HAND, DONE }
}
