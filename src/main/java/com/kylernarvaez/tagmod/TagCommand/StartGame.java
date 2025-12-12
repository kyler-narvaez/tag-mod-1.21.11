package com.kylernarvaez.tagmod.TagCommand;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.Heightmap;
import net.minecraft.util.math.BlockPos;

import net.minecraft.text.Text;

//Command Args
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.world.chunk.ChunkStatus;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static net.minecraft.server.command.CommandManager.argument;



public class StartGame {

    private static BlockPos arenaCenter;
    private static int arenaRadius;

    private static Set<ServerPlayerEntity> seekers = new HashSet<>();
    private static Set<ServerPlayerEntity> runners = new HashSet<>();

    private static int timeLimit = -1;
    private static int tickCounter = 0;
    private static boolean gameRunning = false;

    public static void register() {

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(
                    CommandManager.literal("tagstart")
                            .then(argument("radius", IntegerArgumentType.integer(5))
                                    .then(argument("minutes", IntegerArgumentType.integer(1))
                                            .executes(context -> {
                                                int radius = IntegerArgumentType.getInteger(context, "radius");
                                                int minutes = IntegerArgumentType.getInteger(context, "minutes");

                                                ServerPlayerEntity itPlayer = context.getSource().getPlayer();
                                                context.getSource().sendFeedback(
                                                        () -> Text.literal("Starting Game with radius " + radius + " and time to seek " + minutes + " (minutes)"),
                                                        false
                                                );

                                                //teleportPlayer(player, radius, minutes);
                                                startTagGame(itPlayer, radius, minutes);
                                                return 1;
                                            })
                                    )
                            )
                            .executes(context -> {
                                ServerPlayerEntity itPlayer = context.getSource().getPlayer();
                                //teleportPlayer(player, 1000, 20); // default radius & attempts
                                startTagGame(itPlayer, 20, 5);
                                context.getSource().sendFeedback(
                                        () -> Text.literal("Starting Game with default radius 20 and time 5 minutes"),
                                        false
                                );
                                return 1;
                            })


            );
        });
    }

    private static void startTagGame(ServerPlayerEntity commandPlayer, int radius, int minutes) {
        assignRoles(commandPlayer);
        setupArena(seekers.iterator().next(), radius);

        //Setup game timer
        timeLimit = minutes * 10;
        gameRunning = true;
        commandPlayer.sendMessage(Text.literal("Game has Started! Ends in " + minutes + " minutes."), false);
    }

    private static void assignRoles(ServerPlayerEntity commandPlayer) {
        ServerWorld world = commandPlayer.getEntityWorld();

        //Grab all players
        List<ServerPlayerEntity> players = world.getPlayers();
        giveStartingItems(players);

        //Assign seeker
        ServerPlayerEntity seeker = players.get(new Random().nextInt(players.size()));
        seekers.clear();
        seekers.add(seeker);

        //Assign runners
        for (ServerPlayerEntity p : players) {
            if (p != seeker) {
                runners.add(p);
            }
        }

        //Announce Roles
        for (ServerPlayerEntity p : players) {
            if (seekers.contains(p))
                p.sendMessage(Text.literal("You are a Seeker! Try to tag Everyone"), false);
            else
                p.sendMessage(Text.literal("You are a Runner! Don't Get Tagged"), false);
        }

    }

    private static void giveStartingItems(List<ServerPlayerEntity> players) {
        for (ServerPlayerEntity p : players) {
            p.getInventory().clear();
            p.getInventory().insertStack(new ItemStack(Items.PINK_WOOL, 64));
            p.getInventory().insertStack(new ItemStack(Items.COOKED_BEEF, 16));
        }
    }

    private static void endTagGame(MinecraftServer server) {
        gameRunning = false;
        timeLimit = -1;
        tickCounter = 0;

        for (ServerWorld world : server.getWorlds()) {
            world.getWorldBorder().setSize(30000000);
        }

        //FIXME - Check who won

        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            p.sendMessage(Text.literal("GAME OVER!"), false);
        }

        arenaCenter = null;
        arenaRadius = 0;
        seekers.clear();
        runners.clear();
    }

    private static void setupArena(ServerPlayerEntity itPlayer, int radius) {
        ServerWorld world = itPlayer.getEntityWorld();

        // Teleport the seeker
        BlockPos itPos = teleportItPlayer(itPlayer, radius);
        arenaCenter = itPos;
        arenaRadius = radius;

        //Set world border
        world.getWorldBorder().setCenter(arenaCenter.getX() + 0.5, arenaCenter.getZ() + 0.5);

        world.getWorldBorder().setSize(arenaRadius * 2);

        // Teleport other players near the "it" player
        for (ServerPlayerEntity other : world.getPlayers()) {
            if (other != itPlayer) {
                teleportNear(other, itPos, radius - 1);
            }
        }
    }

    private static void teleportNear(ServerPlayerEntity player, BlockPos center, int radius) {
        ServerWorld world = player.getEntityWorld();

        double angle = Math.random() * 2 * Math.PI;
        double dist = Math.random() * radius;
        int x = (int)(center.getX() + Math.cos(angle) * dist);
        int z = (int)(center.getZ() + Math.sin(angle) * dist);

        int y = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z)).getY();

        player.setVelocity(0,0,0);
        player.teleport(x+0.5, y+1, z+0.5, false);

        player.sendMessage(Text.literal("You are in the tag arena!"), false);
    }

    private static BlockPos teleportItPlayer(ServerPlayerEntity player, int radius) {
        ServerWorld world = player.getEntityWorld();

        int attempts = 0;
        boolean safe = false;

        int x = 0, y = 0, z = 0;

        while (!safe && attempts < 20) {
            attempts++;

            if (attempts != 1) {
                player.sendMessage(Text.literal("Unsafe: " + x + " " + (y+1) + " " + z + " (Trying Again)"), false);
            }

            x = (new Random().nextInt(20000) - 10000);
            z = (new Random().nextInt(20000) - 10000);

            world.getChunkManager().getChunk(x >> 4, z >> 4, ChunkStatus.FULL, true);

            BlockPos pos = new BlockPos(x, 0, z);
            y = world.getTopPosition(
                    Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
                    pos
            ).getY();

            BlockPos checkPos = new BlockPos(x, y, z);

            safe = world.getBlockState(checkPos).getFluidState().isEmpty();
        }

        player.setVelocity(0,0,0);
        player.teleport(x+0.5, y+1, z+0.5, false);
        player.sendMessage(Text.literal("Teleported to: " + x + " " + (y+1) + " " + z), false);
        return new BlockPos(x, y, z);
    }

    public static void onServerTick(MinecraftServer server) {
        if (!gameRunning) return;
        tickCounter++;

        if (tickCounter >= 20) {
            tickCounter = 0;
            timeLimit--;

            if (timeLimit % 60 == 0 || timeLimit <= 10) {
                for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                    p.sendMessage(Text.literal("Time Left: " + timeLimit + "s"), false);
                }
            }
        }
        if (timeLimit <= 0) {
            endTagGame(server);
        }
    }
}
