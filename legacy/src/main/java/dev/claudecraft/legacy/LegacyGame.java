package dev.claudecraft.legacy;

import dev.claudecraft.agent.json.Json;
import dev.claudecraft.core.game.Game;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandResultStats;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
//? if >=1.12 {
/*import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
*///?} else {
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
//?}

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

final class LegacyGame implements Game {
    private static final int MAX_ENTITIES = 40;

    @Override
    public boolean inWorld() {
        return LegacyClient.player() != null && LegacyClient.world() != null;
    }

    @Override
    public Json status() {
        Minecraft minecraft = Minecraft.getMinecraft();
        EntityPlayer player = LegacyClient.player();
        BlockPos pos = new BlockPos(player);
        return Json.object()
            .put("player", player.getName())
            .put("position", Json.object().put("x", round(player.posX)).put("y", round(player.posY)).put("z", round(player.posZ)))
            .put("blockPosition", position(pos))
            .put("facing", player.getHorizontalFacing().getName())
            .put("yaw", round(wrapDegrees(player.rotationYaw)))
            .put("pitch", round(player.rotationPitch))
            .put("dimension", dimension(LegacyClient.world()))
            .put("gameMode", minecraft.playerController.getCurrentGameType().getName())
            .put("health", player.getHealth())
            .put("food", player.getFoodStats().getFoodLevel())
            .put("heldItem", heldItem(player))
            .put("lookingAt", lookingAt(minecraft))
            .put("timeOfDay", LegacyClient.world().getWorldTime() % 24000)
            .put("raining", LegacyClient.world().isRaining())
            .put("singleplayer", minecraft.isSingleplayer());
    }

    private static Json lookingAt(Minecraft minecraft) {
        //? if >=1.12 {
        /*RayTraceResult hit = minecraft.objectMouseOver;
        if (hit == null) return Json.NULL;
        if (hit.typeOfHit == RayTraceResult.Type.BLOCK) {
        *///?} else {
        MovingObjectPosition hit = minecraft.objectMouseOver;
        if (hit == null) return Json.NULL;
        if (hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
        //?}
            return Json.object()
                .put("block", blockId(hit.getBlockPos()))
                .put("position", position(hit.getBlockPos()))
                .put("face", hit.sideHit.getName());
        }
        if (hit.entityHit != null) return Json.object().put("entity", entityType(hit.entityHit)).put("name", hit.entityHit.getName());
        return Json.NULL;
    }

    @Override
    public String block(int x, int y, int z) {
        return blockId(new BlockPos(x, y, z));
    }

    private static String blockId(BlockPos pos) {
        Block block = LegacyClient.world().getBlockState(pos).getBlock();
        //? if >=1.12 {
        /*return String.valueOf(Block.REGISTRY.getNameForObject(block));
        *///?} else
        return String.valueOf(Block.blockRegistry.getNameForObject(block));
    }

    private static String heldItem(EntityPlayer player) {
        //? if >=1.12 {
        /*ItemStack stack = player.getHeldItemMainhand();
        return stack.isEmpty() ? "minecraft:air" : String.valueOf(Item.REGISTRY.getNameForObject(stack.getItem()));
        *///?} else
        return player.getHeldItem() == null ? "minecraft:air" : String.valueOf(Item.itemRegistry.getNameForObject(player.getHeldItem().getItem()));
    }

    private static String dimension(World world) {
        //? if >=1.12 {
        /*return world.provider.getDimensionType().getName();
        *///?} else
        return world.provider.getDimensionName();
    }

    private static String entityType(Entity entity) {
        //? if >=1.12 {
        /*Object key = EntityList.getKey(entity);
        *///?} else
        Object key = EntityList.getEntityString(entity);
        return key != null ? key.toString() : entity instanceof EntityPlayer ? "minecraft:player" : entity.getClass().getSimpleName();
    }

    @Override
    public Json entities(double radius) {
        EntityPlayer player = LegacyClient.player();
        //? if >=1.12 {
        /*List<Entity> nearby = new ArrayList<>(LegacyClient.world().getEntitiesWithinAABBExcludingEntity(player, player.getEntityBoundingBox().grow(radius)));
        *///?} else
        List<Entity> nearby = new ArrayList<>(LegacyClient.world().getEntitiesWithinAABBExcludingEntity(player, player.getEntityBoundingBox().expand(radius, radius, radius)));
        nearby.sort(Comparator.comparingDouble(entity -> distance(player, entity)));
        Json list = Json.array();
        for (Entity entity : nearby.subList(0, Math.min(MAX_ENTITIES, nearby.size()))) {
            list.add(Json.object()
                .put("type", entityType(entity))
                .put("name", entity.getName())
                .put("position", Json.array().add(round(entity.posX)).add(round(entity.posY)).add(round(entity.posZ)))
                .put("distance", round(distance(player, entity))));
        }
        return list;
    }

    private static double distance(Entity from, Entity to) {
        //? if >=1.12 {
        /*return from.getDistance(to);
        *///?} else
        return from.getDistanceToEntity(to);
    }

    @Override
    public CompletableFuture<String> runCommand(String command) {
        Minecraft minecraft = Minecraft.getMinecraft();
        MinecraftServer server = minecraft.getIntegratedServer();
        if (server == null) {
            LegacyClient.player().sendChatMessage("/" + command);
            return CompletableFuture.completedFuture("Sent to the server. Multiplayer servers do not report results to mods, so verify the effect with read_blocks.");
        }
        UUID playerId = LegacyClient.player().getUniqueID();
        CompletableFuture<String> result = new CompletableFuture<>();
        server.addScheduledTask(() -> result.complete(runOnServer(server, playerId, command)));
        return result;
    }

    private static String runOnServer(MinecraftServer server, UUID playerId, String command) {
        //? if >=1.12 {
        /*EntityPlayerMP player = server.getPlayerList().getPlayerByUUID(playerId);
        *///?} else
        EntityPlayerMP player = server.getConfigurationManager().getPlayerByUUID(playerId);
        if (player == null) return "The player is not in this world.";
        List<String> feedback = new ArrayList<>();
        server.getCommandManager().executeCommand(new Capture(player, feedback), command);
        return feedback.isEmpty() ? "Done (the command printed nothing)." : String.join("\n", feedback);
    }

    @Override
    public void message(String text, boolean muted) {
        //? if >=1.12 {
        /*ITextComponent line = new TextComponentString("[Claude] ").setStyle(new Style().setColor(TextFormatting.GOLD));
        line.appendSibling(new TextComponentString(text).setStyle(new Style().setColor(muted ? TextFormatting.GRAY : TextFormatting.WHITE)));
        LegacyClient.player().sendMessage(line);
        *///?} else {
        IChatComponent line = new ChatComponentText("[Claude] ").setChatStyle(new ChatStyle().setColor(EnumChatFormatting.GOLD));
        line.appendSibling(new ChatComponentText(text).setChatStyle(new ChatStyle().setColor(muted ? EnumChatFormatting.GRAY : EnumChatFormatting.WHITE)));
        LegacyClient.player().addChatMessage(line);
        //?}
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

    private static final class Capture implements ICommandSender {
        private final EntityPlayerMP player;
        private final List<String> lines;

        Capture(EntityPlayerMP player, List<String> lines) {
            this.player = player;
            this.lines = lines;
        }

        @Override
        public String getName() {
            return player.getName();
        }

        //? if >=1.12 {
        /*@Override
        public void sendMessage(ITextComponent message) {
            lines.add(message.getUnformattedText());
        }

        @Override
        public boolean canUseCommand(int permissionLevel, String command) {
            return permissionLevel <= 2;
        }

        @Override
        public Vec3d getPositionVector() {
            return player.getPositionVector();
        }

        @Override
        public MinecraftServer getServer() {
            return player.getServer();
        }
        *///?} else {
        @Override
        public IChatComponent getDisplayName() {
            return player.getDisplayName();
        }

        @Override
        public void addChatMessage(IChatComponent message) {
            lines.add(message.getUnformattedText());
        }

        @Override
        public boolean canCommandSenderUseCommand(int permissionLevel, String command) {
            return permissionLevel <= 2;
        }

        @Override
        public Vec3 getPositionVector() {
            return player.getPositionVector();
        }
        //?}

        @Override
        public BlockPos getPosition() {
            return player.getPosition();
        }

        @Override
        public World getEntityWorld() {
            return player.getEntityWorld();
        }

        @Override
        public Entity getCommandSenderEntity() {
            return player;
        }

        @Override
        public boolean sendCommandFeedback() {
            return true;
        }

        @Override
        public void setCommandStat(CommandResultStats.Type type, int amount) {
            player.setCommandStat(type, amount);
        }
    }
}
