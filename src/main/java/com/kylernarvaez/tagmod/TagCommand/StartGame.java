package com.kylernarvaez.tagmod.TagCommand;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.world.Heightmap;
import net.minecraft.util.math.BlockPos;

import net.minecraft.text.Text;

//Command Args
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.world.chunk.ChunkStatus;
import org.apache.logging.log4j.core.jmx.Server;

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
    private static MinecraftServer mainServer = null;


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
                                                mainServer = itPlayer.getEntityWorld().getServer();

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
                                mainServer = itPlayer.getEntityWorld().getServer();
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

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (!gameRunning) return ActionResult.PASS;
            if (!(entity instanceof ServerPlayerEntity target)) return ActionResult.PASS;

            // Only seekers can tag
            if (!seekers.contains(player)) return ActionResult.PASS;

            // Only tag runners
            if (!runners.contains(target)) return ActionResult.PASS;

            tagPlayer(player, target);
            return ActionResult.SUCCESS; // cancel normal attack
        });
    }

    private static void tagPlayer(PlayerEntity seeker, ServerPlayerEntity runner) {
        runners.remove(runner);
        seekers.add(runner);

        seeker.sendMessage(Text.literal("You tagged " + runner.getName().getString() + "!"), false);
        runner.sendMessage(Text.literal("Totally Outplayed Too Be Honest. You Be Seeking Now!"), false);

        //runner.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 60));
        //seeker.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 40));


        if (runners.isEmpty()) {
            endTagGame(seeker.getEntityWorld().getServer());
        }
    }

    private static void startTagGame(ServerPlayerEntity commandPlayer, int radius, int minutes) {
        assignRoles(commandPlayer);
        setupArena(seekers.iterator().next(), radius);

        //Setup game timer
        timeLimit = minutes * 60;
        commandPlayer.sendMessage(Text.literal("Game has Started! Ends in " + minutes + " minutes."), false);
        gameRunning = true;
    }

    private static void assignRoles(ServerPlayerEntity commandPlayer) {
        ServerWorld world = commandPlayer.getEntityWorld();

        //Grab all players
        List<ServerPlayerEntity> players = world.getPlayers();
        for (ServerPlayerEntity p : players) {
            giveStartingItems(p);
        }

        //Assign seeker
        ServerPlayerEntity seeker = players.get(new Random().nextInt(players.size()));
        broadcastMessage(seeker.getName() + " is It! Run Gamers!");
        seeker.sendMessage(Text.literal("You are a Seeker: Zombie Tag Style"), false);
        seekers.clear();
        seekers.add(seeker);

        //Assign runners
        for (ServerPlayerEntity p : players) {
            if (p != seeker) {
                runners.add(p);
                p.sendMessage(Text.literal("RUN FORREST RUN"), false);
            }
        }

    }

    private static void giveStartingItems(ServerPlayerEntity player) {
        player.getInventory().clear();
        int stackCount = seekers.contains(player) ? mainServer.getCurrentPlayerCount() : 1;
        player.getInventory().insertStack(new ItemStack(Items.PINK_WOOL, 64 * stackCount));
        player.getInventory().insertStack(new ItemStack(Items.COOKED_BEEF, 16 * stackCount));
    }

    private static void endTagGame(MinecraftServer server) {
        String winnerString = "Runners Win! Runners do be runnin!";
        if (timeLimit > 0) {
            winnerString = "Seekers Win! Seeking love from no one. . .";
        }
        gameRunning = false;
        timeLimit = -1;
        tickCounter = 0;

        for (ServerWorld world : server.getWorlds()) {
            world.getWorldBorder().setSize(30000000);
        }

        broadcastMessage("GAME OVER! " + winnerString);

        arenaCenter = null;
        arenaRadius = 0;
        seekers.clear();
        runners.clear();
        mainServer = null;
    }

    private static void setupArena(ServerPlayerEntity itPlayer, int radius) {
        ServerWorld world = itPlayer.getEntityWorld();

        // Teleport the seeker
        BlockPos itPos = teleportItPlayer(itPlayer, radius);
        arenaCenter = itPos;
        arenaRadius = radius;

        //Set world border
        world.getWorldBorder().setCenter(arenaCenter.getX(), arenaCenter.getZ());

        world.getWorldBorder().setSize(arenaRadius * 2);

        // Teleport other players near the "it" player
        for (ServerPlayerEntity other : world.getPlayers()) {
            if (other != itPlayer) {
                teleportNear(other, itPos, radius - 2);
            }
        }
    }

    private static void teleportNear(ServerPlayerEntity player, BlockPos center, int radius) {
        ServerWorld world = player.getEntityWorld();

        double angle = Math.random() * 2 * Math.PI;
        double dist = Math.random() * radius;

        int x = (int)(center.getX() + Math.cos(angle) * dist);
        int z = (int)(center.getZ() + Math.sin(angle) * dist);

        world.getChunkManager().getChunk(x >> 4, z >> 4, ChunkStatus.FULL, true);

        int y = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z)).getY();

        player.setVelocity(0,0,0);
        player.teleport(x+0.5, y+1, z+0.5, false);
    }

    private static BlockPos teleportItPlayer(ServerPlayerEntity player, int radius) {
        ServerWorld world = player.getEntityWorld();

        int attempts = 0;
        boolean safe = false;

        int x = 0, y = 0, z = 0;

        while (!safe && attempts < 10) {
            attempts++;

            if (attempts != 1) {
                player.sendMessage(Text.literal("Unsafe: " + x + " " + (y+1) + " " + z + " (Trying Again)"), false);
            } else if (attempts == 10) {
                player.sendMessage(Text.literal("Unsafe: " + x + " " + (y+1) + " " + z + " (Last Attempt)"), false);
            }

            x = new Random().nextInt(20000) - 10000;
            z = new Random().nextInt(20000) - 10000;

            world.getChunkManager().getChunk(x >> 4, z >> 4, ChunkStatus.FULL, true);

            BlockPos pos = new BlockPos(x, 0, z);
            y = world.getTopPosition(
                    Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
                    pos
            ).getY();

            BlockPos ground = new BlockPos(x, y, z);
            BlockPos head = ground.up();

            boolean solidGround = !world.getBlockState(ground).isAir();
            boolean headClear = world.getBlockState(head).isAir();
            boolean noFluid = world.getBlockState(ground).getFluidState().isEmpty();

            safe = solidGround && headClear && noFluid;
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

    public static boolean isGameRunning() {
        return gameRunning;
    }

    public static void onPlayerDeath(ServerPlayerEntity player) {
        if (!runners.contains(player)) return ;

        runners.remove(player);
        seekers.add(player);

        player.sendMessage(Text.literal("IDIOT PLAY! You died dumb dumb, you be seeking now!"), false);
        if (runners.isEmpty()) {
            endTagGame(player.getEntityWorld().getServer());
        }
    }

    public static void onPlayerRespawn(ServerPlayerEntity player) {
        if (arenaCenter == null) return;

        teleportNear(player, arenaCenter, arenaRadius - 1);
        giveStartingItems(player);
        player.sendMessage(
                Text.literal("Seeker: Get back in it bro!"),
                false
        );
    }

    private static void broadcastMessage(String message) {

        List<ServerPlayerEntity> players = mainServer.getPlayerManager().getPlayerList();
        for (ServerPlayerEntity p : players) {
            p.sendMessage(
                    Text.literal(message),
                    false
            );
        }
    }
}
