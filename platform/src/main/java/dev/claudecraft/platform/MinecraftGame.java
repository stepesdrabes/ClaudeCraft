package dev.claudecraft.platform;

import dev.claudecraft.agent.json.Json;
import dev.claudecraft.core.game.Game;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
//? if >=1.21.11
import net.minecraft.server.permissions.LevelBasedPermissionSet;
//? if >=1.19.3 {
import net.minecraft.core.registries.BuiltInRegistries;
//?} else
//import net.minecraft.core.Registry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

final class MinecraftGame implements Game {
    private static final int MAX_ENTITIES = 40;
    private static final int CLAUDE_COLOR = 0xE08A6A;

    @Override
    public boolean inWorld() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null && minecraft.level != null;
    }

    @Override
    public Json status() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        BlockPos pos = player.blockPosition();
        return Json.object()
            .put("player", player.getName().getString())
            .put("position", Json.object().put("x", round(player.getX())).put("y", round(player.getY())).put("z", round(player.getZ())))
            .put("blockPosition", position(pos))
            .put("facing", player.getDirection().getName())
            .put("yaw", round(wrapDegrees(yaw(player))))
            .put("pitch", round(pitch(player)))
            .put("dimension", dimension(minecraft))
            .put("gameMode", minecraft.gameMode.getPlayerMode().getName())
            .put("health", player.getHealth())
            .put("food", player.getFoodData().getFoodLevel())
            .put("heldItem", itemId(player.getMainHandItem().getItem()))
            .put("lookingAt", lookingAt(minecraft))
            .put("raining", minecraft.level.isRaining())
            .put("singleplayer", minecraft.hasSingleplayerServer());
    }

    private static Json lookingAt(Minecraft minecraft) {
        HitResult hit = minecraft.hitResult;
        if (hit instanceof BlockHitResult && hit.getType() == HitResult.Type.BLOCK) {
            BlockHitResult blockHit = (BlockHitResult) hit;
            return Json.object()
                .put("block", blockId(minecraft, blockHit.getBlockPos()))
                .put("position", position(blockHit.getBlockPos()))
                .put("face", blockHit.getDirection().getName());
        }
        if (hit instanceof EntityHitResult) {
            Entity entity = ((EntityHitResult) hit).getEntity();
            return Json.object().put("entity", EntityType.getKey(entity.getType()).toString()).put("name", entity.getName().getString());
        }
        return Json.NULL;
    }

    @Override
    public String block(int x, int y, int z) {
        return blockId(Minecraft.getInstance(), new BlockPos(x, y, z));
    }

    @Override
    public Json entities(double radius) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        List<Entity> nearby = new ArrayList<>(minecraft.level.getEntities(player, player.getBoundingBox().inflate(radius), entity -> true));
        nearby.sort(Comparator.comparingDouble(player::distanceTo));
        Json list = Json.array();
        for (Entity entity : nearby.subList(0, Math.min(MAX_ENTITIES, nearby.size()))) {
            list.add(Json.object()
                .put("type", EntityType.getKey(entity.getType()).toString())
                .put("name", entity.getName().getString())
                .put("position", Json.array().add(round(entity.getX())).add(round(entity.getY())).add(round(entity.getZ())))
                .put("distance", round(player.distanceTo(entity))));
        }
        return list;
    }

    @Override
    public CompletableFuture<String> runCommand(String command) {
        Minecraft minecraft = Minecraft.getInstance();
        MinecraftServer server = minecraft.getSingleplayerServer();
        if (server == null) {
            sendToServer(minecraft.player, command);
            return CompletableFuture.completedFuture("Sent to the server. Multiplayer servers do not report results to mods, so verify the effect with read_blocks.");
        }
        UUID playerId = minecraft.player.getUUID();
        CompletableFuture<String> result = new CompletableFuture<>();
        server.execute(() -> result.complete(runOnServer(server, playerId, command)));
        return result;
    }

    private static void sendToServer(LocalPlayer player, String command) {
        //? if >=1.19.3 {
        player.connection.sendCommand(command);
        //?} elif >=1.19 {
        /*player.commandSigned(command, null);
        *///?} else
        //player.chat("/" + command);
    }

    private static String runOnServer(MinecraftServer server, UUID playerId, String command) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player == null) return "The player is not in this world.";
        List<String> feedback = new ArrayList<>();
        //? if >=1.17 {
        CommandSourceStack source = player.createCommandSourceStack().withSource(new Capture(feedback));
        //?} else {
        /*CommandSourceStack source = new CommandSourceStack(new Capture(feedback), player.position(), player.getRotationVector(),
            player.getLevel(), 2, player.getName().getString(), player.getDisplayName(), server, player);
        *///?}
        //? if >=1.21.11 {
        source = source.withPermission(LevelBasedPermissionSet.GAMEMASTER);
        //?} else
        //source = source.withPermission(2);
        //? if >=1.19 {
        server.getCommands().performPrefixedCommand(source, command);
        //?} else
        //server.getCommands().performCommand(source, command);
        return feedback.isEmpty() ? "Done (the command printed nothing)." : String.join("\n", feedback);
    }

    @Override
    public void message(String text, boolean muted) {
        Component line = Text.colored("[Claude] ", CLAUDE_COLOR).append(Text.colored(text, muted ? 0xAAAAAA : 0xFFFFFF));
        //? if >=26.1 {
        Minecraft.getInstance().player.sendSystemMessage(line);
        //?} else
        //Minecraft.getInstance().player.displayClientMessage(line, false);
    }

    private static String dimension(Minecraft minecraft) {
        //? if >=1.21.11 {
        return minecraft.level.dimension().identifier().toString();
        //?} else
        //return minecraft.level.dimension().location().toString();
    }

    private static String blockId(Minecraft minecraft, BlockPos pos) {
        Block block = minecraft.level.getBlockState(pos).getBlock();
        //? if >=1.19.3 {
        return BuiltInRegistries.BLOCK.getKey(block).toString();
        //?} else
        //return Registry.BLOCK.getKey(block).toString();
    }

    private static String itemId(Item item) {
        //? if >=1.19.3 {
        return BuiltInRegistries.ITEM.getKey(item).toString();
        //?} else
        //return Registry.ITEM.getKey(item).toString();
    }

    private static float yaw(Entity entity) {
        //? if >=1.17 {
        return entity.getYRot();
        //?} else
        //return entity.yRot;
    }

    private static float pitch(Entity entity) {
        //? if >=1.17 {
        return entity.getXRot();
        //?} else
        //return entity.xRot;
    }

    private static Json position(BlockPos pos) {
        return Json.array().add(pos.getX()).add(pos.getY()).add(pos.getZ());
    }

    private static double wrapDegrees(double degrees) {
        double wrapped = degrees % 360;
        if (wrapped >= 180) wrapped -= 360;
        if (wrapped < -180) wrapped += 360;
        return wrapped;
    }

    private static double round(double value) {
        return Math.round(value * 10) / 10.0;
    }

    private static final class Capture implements CommandSource {
        private final List<String> lines;

        Capture(List<String> lines) {
            this.lines = lines;
        }

        //? if >=1.19 {
        @Override
        public void sendSystemMessage(Component message) {
            lines.add(message.getString());
        }
        //?} else {
        /*@Override
        public void sendMessage(Component message, UUID sender) {
            lines.add(message.getString());
        }
        *///?}

        @Override
        public boolean acceptsSuccess() {
            return true;
        }

        @Override
        public boolean acceptsFailure() {
            return true;
        }

        @Override
        public boolean shouldInformAdmins() {
            return false;
        }
    }
}
