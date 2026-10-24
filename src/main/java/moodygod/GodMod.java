package moodygod;

import java.util.*;
import java.util.stream.Collectors;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public class GodMod implements ModInitializer {
    public static final String ID = "moody_god";
    static final String SATAN = "\u0001";
    static final int ACTIVE_RULES = 120;
    static final int SCENE_VISIT = 4;      // сцена с Кларком — на 4-й визит, один раз
    static final Random R = new Random();
    static final Map<UUID, Rules.PState> STATE = new HashMap<>();
    record Task(long at, Runnable run) {}
    static final List<Task> TASKS = new ArrayList<>();
    static final Set<String> FIRSTS = new HashSet<>();
    static final Set<UUID> BURNED = new HashSet<>();
    static final Map<UUID, Long> MUTED = new HashMap<>();
    static final String[] HOSTILE = {"zombie", "skeleton", "creeper", "spider", "cave_spider", "enderman", "witch", "husk",
        "stray", "drowned", "pillager", "vindicator", "slime", "zombie_villager"};
    static final Set<String> WATCH = new HashSet<>();
    static MinecraftServer SRV;
    static long tick = 0, nextVisit = 3600;   // ПЕРВЫЙ визит всегда через 3 минуты
    static long speakingUntil = 0, chatCool = 0;
    static boolean busy = false, forceWrath = false, satanThisVisit = false;
    static int badStreak = 0, visitNo = 0, satanNext = 5;  // Сатана: первый раз на 5-й визит, потом каждые 4-5
    static List<Rules.Rule> active;

    static Rules.PState st(ServerPlayer p) { return STATE.computeIfAbsent(p.getUUID(), k -> new Rules.PState()); }
    static void schedule(int delay, Runnable r) { TASKS.add(new Task(tick + Math.max(0, delay), r)); }
    static int interval() { return 6000 + R.nextInt(4801); } // 5..9 минут
    static String pathB(Block b) { return BuiltInRegistries.BLOCK.getKey(b).getPath(); }
    static String pathI(ItemStack s) { return BuiltInRegistries.ITEM.getKey(s.getItem()).getPath(); }
    static boolean online(MinecraftServer s, ServerPlayer p) { return s.getPlayerList().getPlayer(p.getUUID()) != null; }

    static boolean prayed(ServerPlayer sp) {
        Rules.PState s = st(sp);
        return s.prayTick > 0 && tick - s.prayTick <= 1200;
    }
    static void firstEvent(ServerPlayer sp, String key) {
        if (!FIRSTS.add(key)) return;
        st(sp).inc((prayed(sp) ? "p_" : "np_") + key);
    }

    static String blockKey(BlockState state, String id) {
        if (id.endsWith("iron_ore")) return "iron";
        if (id.endsWith("coal_ore")) return "coal";
        if (id.endsWith("diamond_ore")) return "diamond";
        if (id.endsWith("redstone_ore")) return "redstone";
        if (id.endsWith("lapis_ore")) return "lapis";
        if (id.endsWith("emerald_ore")) return "emerald";
        if (id.endsWith("copper_ore")) return "copper";
        if (id.equals("ancient_debris")) return "debris";
        if (id.equals("obsidian")) return "obsidian";
        if (state.is(BlockTags.LOGS)) return "logs";
        if (state.is(BlockTags.LEAVES)) return "leaves";
        if (state.is(BlockTags.FLOWERS)) return "flower";
        if (state.is(BlockTags.CROPS)) return "crops";
        if (id.equals("dirt") || id.equals("grass_block")) return "dirt";
        if (id.equals("sand")) return "sand";
        if (id.equals("gravel")) return "gravel";
        if (id.equals("stone") || id.equals("cobblestone")) return "stone";
        if (id.equals("glass")) return "glass";
        if (state.is(BlockTags.WOOL)) return "wool";
        if (state.is(BlockTags.BEDS)) return "bed";
        return null;
    }

    @Override
    public void onInitialize() {
        for (String[] a : Rules2.FIRST_ITEMS) WATCH.add(a[0]);
        PayloadTypeRegistry.clientboundPlay().register(GodPayload.TYPE, GodPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(TimerPayload.TYPE, TimerPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(FxPayload.TYPE, FxPayload.CODEC);
        ServerTickEvents.END_SERVER_TICK.register(this::onTick);

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
            ServerPlayNetworking.send(handler.getPlayer(), new TimerPayload(busy ? -1 : (int) Math.max(0, nextVisit - tick), 0)));

        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((msg, sender, params) -> {
            Long u = MUTED.get(sender.getUUID());
            if (u != null && tick < u) { note(sender, "§c[Речевой центр удалён] Ты не можешь говорить."); return false; }
            return true;
        });

        ServerMessageEvents.CHAT_MESSAGE.register((msg, sender, params) -> {
            String raw = msg.signedContent();
            String t = raw.toLowerCase(Locale.ROOT);
            Rules.PState s = st(sender);
            s.inc("msgs");
            s.chat.append(' ').append(t).append(' ');
            if (raw.length() >= 4 && raw.equals(raw.toUpperCase(Locale.ROOT)) && !raw.equals(t)) s.inc("caps");
            if (Scene.active || SRV == null) return;
            if (busy && tick < speakingUntil) { interrupt(sender); return; }
            if (tick >= chatCool && !busy) {
                List<ServerPlayer> all = SRV.getPlayerList().getPlayers();
                if (t.contains("овц") && R.nextInt(100) < 35) {
                    chatCool = tick + 600;
                    say(all, Lines.pages("ТЫ СКАЗАЛ «ОВЦА»?! Я СЛЫШАЛ! Я ВСЕГДА СЛЫШУ!"), 4);
                } else if (t.contains("бог") && R.nextInt(100) < 25) {
                    chatCool = tick + 600;
                    say(all, Lines.pages("Меня звали? Занят. Приду позже. Или не приду."), 0);
                }
            }
        });

        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, be) -> {
            if (!(player instanceof ServerPlayer sp)) return;
            Rules.PState s = st(sp);
            boolean sn = sp.isShiftKeyDown();
            s.inc("blocks");
            String k = blockKey(state, pathB(state.getBlock()));
            if (k == null) return;
            if (k.equals("iron") || k.equals("coal")) s.inc(k + (sn ? "S" : "N")); else s.inc(k);
            String h = pathI(sp.getMainHandItem());
            if (k.equals("dirt") && !h.endsWith("shovel")) s.inc("dirtHand");
            if (k.equals("logs") && !h.endsWith("_axe")) s.inc("logsNoAxe");
            if (k.equals("stone") && !h.endsWith("pickaxe")) s.inc("stoneHand");
            firstEvent(sp, "b_" + k);
        });

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayer sp) st(sp).inc("deaths");
            if (source.getEntity() instanceof ServerPlayer killer) {
                st(killer).inc("k_" + net.minecraft.world.entity.EntityType.getKey(entity.getType()).getPath());
                firstEvent(killer, "kill");
            }
        });

        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayer sp) st(sp).inc("hurt");
            if (source.getEntity() instanceof ServerPlayer atk && atk.getMainHandItem().isEmpty()) st(atk).inc("fist");
            return true;
        });

        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (player instanceof ServerPlayer sp && Rules.is(entity, "sheep")) {
                ItemStack it = sp.getItemInHand(hand);
                Rules.PState s = st(sp);
                if (it.is(Items.SHEARS)) s.inc("shear");
                else if (it.is(Items.LEAD)) s.inc("lead");
                else if (it.is(Items.WHEAT)) s.inc("feed");
            }
            return InteractionResult.PASS;
        });

        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
            Rules.PState s = st(sp);
            String bid = pathB(level.getBlockState(hit.getBlockPos()).getBlock());
            ItemStack held = sp.getItemInHand(hand);
            String hid = pathI(held);
            boolean sn = sp.isShiftKeyDown();
            if (bid.endsWith("chest") || bid.equals("barrel")) { if (!sn) s.inc("chestN"); }
            else if (bid.endsWith("_door") && !bid.startsWith("iron")) { if (hand == InteractionHand.MAIN_HAND) s.inc("doorMain"); }
            else if (bid.endsWith("trapdoor")) s.inc("trap");
            else if (bid.equals("lever") || bid.endsWith("_button")) s.inc("lever");
            else if (bid.equals("note_block")) s.inc("noteblock");
            else if (bid.endsWith("anvil")) s.inc("anvil");
            else if (bid.equals("enchanting_table")) s.inc("enchant");
            else if (bid.equals("composter")) s.inc("composter");
            else if (bid.endsWith("furnace") || bid.equals("smoker")) { if (!sn) s.inc("furnaceN"); }
            if (held.getItem() instanceof BlockItem) {
                s.inc("placed");
                if (hid.endsWith("torch")) { if (!sn) s.inc("tn"); }
                else if (hid.endsWith("sapling")) s.inc("placed_sapling");
                else if (hid.equals("tnt")) s.inc("placed_tnt");
                else if (hid.equals("dirt")) s.inc("placed_dirt");
                if (hid.equals("sea_pickle") && held.getHoverName().getString().contains("Пикл Рик")) pickleBoom(sp);
            }
            return InteractionResult.PASS;
        });

        // /god — вызвать Бога сейчас (тест), /god wrath — сразу в гневе
        CommandRegistrationCallback.EVENT.register((d, reg, env) -> d.register(Commands.literal("god")
            .executes(c -> { startVisit(c.getSource().getServer(), false); return 1; })
            .then(Commands.literal("wrath").executes(c -> { startVisit(c.getSource().getServer(), true); return 1; }))));
    }

    // ---------- ТИК ----------
    void onTick(MinecraftServer server) {
        SRV = server;
        tick++;
        for (Task t : new ArrayList<>(TASKS)) {
            if (t.at() <= tick) {
                TASKS.remove(t);
                try { t.run().run(); } catch (Exception ignored) {}
            }
        }
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) { nextVisit++; return; } // таймер стоит, пока никого нет
        if (Scene.active && Scene.lock) Scene.lockTick(server);
        for (ServerPlayer p : players) {
            Rules.PState s = st(p);
            double x = p.getX(), y = p.getY(), z = p.getZ();
            if (s.wasGround && !p.onGround() && y - s.lastY > 0.2) s.inc("jumps");
            s.wasGround = p.onGround();
            double dx = x - s.lastX, dz = z - s.lastZ, d = Math.sqrt(dx * dx + dz * dz);
            if (d < 8 && s.lastX != 0) { s.dAcc += d; while (s.dAcc >= 1) { s.dAcc -= 1; s.inc("dist"); } }
            s.lastX = x; s.lastY = y; s.lastZ = z;
            // молитва: присесть и покрутить головой вверх-вниз
            if (p.isShiftKeyDown()) {
                float pit = p.getXRot();
                if (pit < -40) { if (s.look == 2) { s.prayTick = tick; s.look = 0; s.inc("prayed"); } else s.look = 1; }
                else if (pit > 40) { if (s.look == 1) { s.prayTick = tick; s.look = 0; s.inc("prayed"); } else s.look = 2; }
            } else s.look = 0;
            if (tick % 20 == 0) {
                ServerLevel lv = (ServerLevel) p.level();
                if (p.isSprinting()) s.inc("sprintSec");
                if (p.isShiftKeyDown()) s.inc("sneakSec");
                if (p.isSwimming()) s.inc("swimSec");
                if (p.isOnFire()) s.inc("fireSec");
                if (p.isFallFlying()) s.inc("flySec");
                if (p.isPassenger()) s.inc("rideSec");
                if (p.isSleeping()) s.inc("sleepSec");
                if (lv.isRaining() && lv.canSeeSky(p.blockPosition())) s.inc("rainSec");
                if (lv.getMaxLocalRawBrightness(p.blockPosition()) <= 3) s.inc("darkSec");
                // первые предметы в мире
                var inv = p.getInventory();
                for (int i = 0; i < inv.getContainerSize(); i++) {
                    ItemStack it = inv.getItem(i);
                    if (it.isEmpty()) continue;
                    String ip = pathI(it);
                    if (WATCH.contains(ip)) firstEvent(p, "i_" + ip);
                }
                // рука с неба во время визита (только на поверхности)
                if (busy) {
                    boolean surface = !Scene.active && lv.dimension() == Level.OVERWORLD && lv.canSeeSky(p.blockPosition());
                    ServerPlayNetworking.send(p, new FxPayload("hand", surface ? "1" : "0", 0));
                }
            }
            // сожжённые дары
            if (tick % 10 == 0) {
                ServerLevel lv = (ServerLevel) p.level();
                for (Entity e : lv.getEntities((Entity) null, p.getBoundingBox().inflate(24), x2 -> x2 instanceof ItemEntity)) {
                    ItemEntity ie = (ItemEntity) e;
                    if (ie.isOnFire() && ie.getItem().getHoverName().getString().startsWith("Дар") && BURNED.add(ie.getUUID())) giftBurned(server, p);
                }
            }
        }
        // за 10 секунд до визита враждебные мобы нападают на овец
        long left = nextVisit - tick;
        if (!busy && !Scene.active && left <= 200 && left > 0 && tick % 20 == 0) sheepWar(server, players);
        if (!busy && tick >= nextVisit) startVisit(server, false);
    }

    // ---------- УТИЛИТЫ ----------
    static void serverCmd(MinecraftServer s, String c) {
        try { s.getCommands().performPrefixedCommand(s.createCommandSourceStack().withSuppressedOutput(), c); } catch (Exception ignored) {}
    }
    static void cmd(MinecraftServer s, ServerPlayer p, String suffix) {
        serverCmd(s, "execute as " + p.getStringUUID() + " at @s " + suffix);
    }
    static void note(ServerPlayer p, String text) { p.sendSystemMessage(Component.literal(text)); }
    static void sendFx(Collection<ServerPlayer> ps, String op, String a, int n) {
        FxPayload f = new FxPayload(op, a, n);
        for (ServerPlayer p : ps) ServerPlayNetworking.send(p, f);
    }
    static void sendTimer(MinecraftServer server, int ticks) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) ServerPlayNetworking.send(p, new TimerPayload(ticks, 0));
    }

    static int say(Collection<ServerPlayer> ps, List<String> pages, int mood) {
        GodPayload pl = new GodPayload(String.join("\f", pages), mood);
        for (ServerPlayer p : ps) ServerPlayNetworking.send(p, pl);
        int ms = 0;
        for (String pg : pages) ms += GodPayload.pageMs(GodPayload.strip(pg));
        int ticks = ms / 50 + 15;
        if (busy) speakingUntil = Math.max(speakingUntil, tick + ticks);
        return ticks;
    }

    static void interrupt(ServerPlayer sinner) {
        List<ServerPlayer> all = SRV.getPlayerList().getPlayers();
        sendFx(all, "interrupt", "", 0);
        note(sinner, "§c[Бог] §fТы перебил Бога.");
        schedule(25, () -> {
            cmd(SRV, sinner, "run summon minecraft:lightning_bolt ~ ~ ~");
            cmd(SRV, sinner, "run kill @s");
        });
    }

    static void pickleBoom(ServerPlayer sp) {
        note(sp, "§a[Рик] §fТы поставил Пикл Рика. Рик не прощает.");
        cmd(SRV, sp, "run particle minecraft:explosion_emitter ~ ~1 ~");
        cmd(SRV, sp, "run playsound minecraft:entity.generic.explode master @a ~ ~ ~ 2");
        cmd(SRV, sp, "run damage @s 12 minecraft:explosion");
        cmd(SRV, sp, "run clear @s minecraft:sea_pickle 1");
    }

    static void giftBurned(MinecraftServer server, ServerPlayer p) {
        int t = say(List.of(p), Lines.pages("ТЫ СЖЁГ МОЙ ДАР?! Я ДАЛ ТЕБЕ ПОДАРОК, А ТЫ ЕГО СЖЁГ?! ЗНАЧИТ, ЖОПА. ПОЛНАЯ."), 4);
        schedule(t, () -> applyPun(server, p, Puns.GIFT_BURN, 120));
    }

    static void sheepWar(MinecraftServer server, List<ServerPlayer> ps) {
        for (ServerPlayer p : ps)
            for (String t : HOSTILE) {
                String near = "if entity @e[type=minecraft:" + t + ",distance=..16] ";
                cmd(server, p, "as @e[type=minecraft:sheep,distance=..30] at @s " + near + "as @n[type=minecraft:" + t + ",distance=..16] run tp @s ~ ~ ~");
                cmd(server, p, "as @e[type=minecraft:sheep,distance=..30] at @s " + near + "run damage @s 3 minecraft:mob_attack");
            }
    }

    static void freezeWorld(MinecraftServer server, List<ServerPlayer> ps, int ticks) {
        int sec = Math.max(5, ticks / 20 + 1);
        for (ServerPlayer p : ps) {
            cmd(server, p, "run effect give @s minecraft:slowness " + sec + " 255 true");
            cmd(server, p, "run effect give @s minecraft:jump_boost " + sec + " 200 true");
            for (String t : HOSTILE) {
                String sel = "as @e[type=minecraft:" + t + ",distance=..48] at @s run ";
                cmd(server, p, sel + "data merge entity @s {NoAI:1b}");
                cmd(server, p, sel + "tag @s add godfrozen");
                cmd(server, p, sel + "tp @s ~ ~ ~ ~ -90");
            }
        }
    }
    static void unfreezeWorld(MinecraftServer server) {
        serverCmd(server, "execute as @e[tag=godfrozen] run data merge entity @s {NoAI:0b}");
        serverCmd(server, "tag @e[tag=godfrozen] remove godfrozen");
    }

    static void weather(MinecraftServer server, int mood) {
        if (mood == 4) serverCmd(server, "weather thunder");
        else if (mood == 2) serverCmd(server, "weather rain");
        else if (mood == 1) serverCmd(server, "weather clear");
    }

    static int pickMood() {
        if (forceWrath) { forceWrath = false; return 4; }
        int r = R.nextInt(100);
        if (r < 55) return r < 14 ? 0 : r < 24 ? 1 : r < 32 ? 2 : r < 44 ? 3 : 4;
        return 5 + R.nextInt(10);
    }

    static void ensureActive(MinecraftServer server) {
        if (active != null) return;
        List<Rules.Rule> l = new ArrayList<>(Rules.ALL);
        l.addAll(Rules2.EXTRA);
        Collections.shuffle(l, new Random(server.overworld().getSeed()));
        active = new ArrayList<>(l.subList(0, Math.min(ACTIVE_RULES, l.size())));
    }

    static String pickStory() {
        int r = R.nextInt(100);
        return r < 30 ? Lines.pick(Lines.NETFLIX) : r < 55 ? Lines.pick(Lines.STORY) : Lines.pick(Lines2.STORY2);
    }

    // ---------- ВИЗИТ ----------
    static void startVisit(MinecraftServer server, boolean wrath) {
        List<ServerPlayer> ps = new ArrayList<>(server.getPlayerList().getPlayers());
        if (ps.isEmpty() || busy) return;
        busy = true;
        visitNo++;
        sendTimer(server, -1);
        ensureActive(server);
        boolean allOver = ps.stream().allMatch(p -> p.level().dimension() == Level.OVERWORLD);
        if (visitNo >= SCENE_VISIT && !Scene.done && allOver && !wrath) { Scene.start(server, ps); return; }

        int m0 = wrath ? 4 : pickMood();
        weather(server, m0);
        satanThisVisit = visitNo >= satanNext;
        if (satanThisVisit) satanNext = visitNo + 4 + R.nextInt(2);
        boolean disco = !wrath && !satanThisVisit && R.nextInt(100) < 10;
        String names = ps.stream().map(p -> p.getName().getString()).collect(Collectors.joining(" и "));

        List<String> pages = new ArrayList<>(Lines.pages(Lines.pick(Lines2.intro(m0)).replace("{n}", names)));
        int introCount = pages.size();

        if (disco) {
            pages.addAll(Lines.pages("Ладно. Достали. Устроим диско. Я напишу вам песню. Петь не умею, поэтому текстом."));
            int t1 = say(ps, pages, m0);
            freezeWorld(server, ps, t1);
            schedule(t1, () -> {
                List<String> ly = new ArrayList<>();
                for (String s : Lines2.LYRICS) ly.addAll(Lines.pages(s));
                int t2 = say(ps, ly, m0);
                disco(server, ps, t2);
                freezeWorld(server, ps, t2);
                schedule(t2, () -> verdict(server, m0, ps));
            });
            return;
        }

        if (R.nextInt(100) < 45 || m0 == 4) pages.addAll(Lines.pages(Lines.pick(Lines.SHEEP_RANT)));
        if (R.nextInt(100) < 25) pages.addAll(Lines.pages(Lines.pick(Lines2.FILLER)));
        if (R.nextInt(100) < 70) pages.addAll(Lines.pages(pickStory()));
        if (R.nextInt(100) < 45) pages.addAll(Lines.pages(Lines.pick(Lines2.REFS)));
        if (R.nextInt(100) < 20) pages.addAll(Lines.pages(Lines.pick(Lines2.ADS)));
        if (R.nextInt(100) < 15) pages.addAll(Lines.pages(Lines.pick(Lines2.NOTIFY)));
        if (R.nextInt(100) < 35) pages.addAll(Lines.pages("Запомните, смертные! " + active.get(R.nextInt(active.size())).reveal()));
        if (satanThisVisit) { // Сатана перебивает снизу
            for (int i = 0; i < 2; i++)
                for (String pg : Lines.pages(R.nextBoolean() ? Lines.pick(Lines.SATAN) : Lines.pick(Lines2.SATAN2))) pages.add(SATAN + pg);
            pages.addAll(Lines.pages(Lines.pick(Lines.GOD_REPLY)));
        }
        int cur = m0;
        if (!wrath && R.nextInt(100) < 35) { // характер меняется ПОСРЕДИ речи
            cur = (m0 + 1 + R.nextInt(14)) % 15;
            for (int i = introCount; i < pages.size(); i++)
                if (!GodPayload.isSatan(pages.get(i))) pages.set(i, GodPayload.tag(cur) + pages.get(i));
        }
        int ticks = say(ps, pages, m0);
        freezeWorld(server, ps, ticks);
        final int fm = cur;
        schedule(ticks, () -> verdict(server, fm, ps));
    }

    static void disco(MinecraftServer server, List<ServerPlayer> ps, int ticks) {
        String[] types = {"cow", "pig", "chicken", "sheep", "cat", "wolf", "rabbit", "frog", "llama", "fox", "villager", "bee"};
        for (ServerPlayer p : ps)
            for (int i = 0; i < 20; i++) {
                int dx = R.nextInt(13) - 6, dz = R.nextInt(13) - 6;
                if (dx == 0 && dz == 0) dx = 3;
                cmd(server, p, "run summon minecraft:" + types[R.nextInt(types.length)] + " ~" + dx + " ~1 ~" + dz
                    + " {NoAI:1b,Invulnerable:1b,Silent:1b,PersistenceRequired:1b,Tags:[\"disco\"]}");
            }
        sendFx(ps, "disco", "", ticks);
        double[] bass = {0.5, 0.5, 0.75, 0.5, 0.67, 0.5, 0.9, 0.5};
        for (int i = 0; i * 5 < ticks; i++) {
            final int k = i;
            schedule(i * 5, () -> {
                serverCmd(server, "execute as @e[tag=disco] at @s run tp @s ~ ~ ~ ~9 " + (k % 2 == 0 ? "25" : "-15"));
                for (ServerPlayer p : ps) {
                    if (k % 2 == 0) cmd(server, p, String.format(Locale.ROOT, "run playsound minecraft:block.note_block.bass master @s ~ ~ ~ 1.5 %.2f", bass[(k / 2) % bass.length]));
                    else cmd(server, p, "run playsound minecraft:block.note_block.snare master @s ~ ~ ~ 1 1");
                    cmd(server, p, "run playsound minecraft:block.note_block.hat master @s ~ ~ ~ 0.6 2");
                }
            });
        }
        schedule(ticks, () -> {
            sendFx(ps, "disco_end", "", 0);
            serverCmd(server, "execute as @e[tag=disco] at @s run particle minecraft:happy_villager ~ ~1 ~ 0.3 0.3 0.3 0 5");
            serverCmd(server, "kill @e[tag=disco]");
        });
    }

    // ---------- ВЕРДИКТ ----------
    static String violLine(Rules.Rule r) {
        if (r.bad() != null && R.nextInt(100) < 70) return r.bad();
        return Lines.pick(Lines.BAD_TPL).replace("{v}", r.what());
    }

    static void verdict(MinecraftServer server, int mood, List<ServerPlayer> ps) {
        int longest = 100;
        boolean anyBad = false;
        for (ServerPlayer p : ps) {
            if (!online(server, p)) continue;
            Rules.PState s = st(p);
            ServerLevel lv = (ServerLevel) p.level();
            Rules.Ctx c = new Rules.Ctx(p, lv, s, ps);
            int score = 0;
            List<Rules.Rule> viol = new ArrayList<>(), ok = new ArrayList<>();
            for (Rules.Rule r : active) {
                int v;
                try { v = r.eval().applyAsInt(c); } catch (Exception ex) { v = 0; }
                score += v;
                if (v < 0) viol.add(r); else if (v > 0) ok.add(r);
            }
            // --- ОВЦЫ: всегда включены ---
            List<String> sk = new ArrayList<>();
            boolean sheepSin = false;
            var below = lv.getBlockState(p.blockPosition().below());
            if (!lv.getEntities((Entity) null, p.getBoundingBox().inflate(10), e -> Rules.is(e, "sheep")).isEmpty()) { score -= 3; sk.add("near"); sheepSin = true; }
            if (p.getMainHandItem().is(ItemTags.WOOL)) { score -= 2; sk.add("wool"); sheepSin = true; }
            if (s.n("shear") > 0) { score -= 2; sk.add("shear"); sheepSin = true; }
            if (s.n("feed") > 0) { score -= 2; sk.add("feed"); sheepSin = true; }
            if (s.n("lead") > 0) { score -= 2; sk.add("lead"); sheepSin = true; }
            if (s.n("sleepSec") > 0) { score -= 2; sk.add("sleep"); sheepSin = true; }
            if (below.is(BlockTags.WOOL)) { score -= 2; sk.add("stand"); sheepSin = true; }
            if (Rules.countIf(p, it -> it.is(ItemTags.WOOL) || it.is(ItemTags.BEDS) || it.is(ItemTags.WOOL_CARPETS)) > 0) { score -= 1; sk.add("inv"); sheepSin = true; }
            if (s.n("k_sheep") > 0) { score += Math.min(3, s.n("k_sheep") * 2); sk.add("killed"); }
            if (s.n("wool") + s.n("bed") > 0) { score += 1; sk.add("break"); }
            if (Rules.count(p, Items.MUTTON) + Rules.count(p, Items.COOKED_MUTTON) > 0) { score += 1; sk.add("mutton"); }
            if (mood == 4) score -= 2; else if (mood == 1 || mood == 3) score += 1;
            s.reset();

            int sev = score <= -7 ? 3 : score <= -4 ? 2 : score <= -1 ? 1 : 0;
            if (mood == 4 && score <= -1) sev = 3;
            if (score <= -2) anyBad = true;

            // --- наказания ---
            Collections.shuffle(viol, R);
            List<Puns.Pun> puns = new ArrayList<>();
            int tier = Math.max(1, sev);
            if (sheepSin) puns.add(Puns.SHEEP);
            for (int i = 0; i < Math.min(2, viol.size()); i++) {
                Rules.Rule r = viol.get(i);
                if (sev == 0 && R.nextInt(100) >= 60) continue;
                Puns.Pun u = Puns.UNIQUE.get(r.id());
                if (u == null && r.id().startsWith("first_")) u = Puns.NOPRAY;
                puns.add(u != null ? u : Puns.random(tier, R));
            }
            if (sev >= 1 && puns.isEmpty()) puns.add(Puns.random(tier, R));
            while (puns.size() > 3) puns.remove(puns.size() - 1);
            int base = tier == 1 ? 40 : tier == 2 ? 90 : 180;

            // --- РЕЧЬ ВЕРДИКТА ---
            List<String> pages = new ArrayList<>();
            Collections.shuffle(sk, R);
            for (int i = 0; i < Math.min(2, sk.size()); i++) pages.addAll(Lines.pages(Lines.pick(Lines.SHEEP_SIN.get(sk.get(i)))));
            if (score <= -1) pages.addAll(Lines.pages(Lines.pick(Lines2.bad(mood))));
            else if (score >= 1) pages.addAll(Lines.pages(Lines.pick(Lines2.good(mood))));
            else pages.addAll(Lines.pages(Lines.pick(Lines.NEUTRAL)));
            for (int i = 0; i < Math.min(3, viol.size()); i++) {
                pages.addAll(Lines.pages(violLine(viol.get(i))));
                note(p, "§8[Журнал грехов] §7Ты " + viol.get(i).what() + ".");
            }
            if (viol.size() > 3) pages.addAll(Lines.pages(Lines.pick(Lines.MORE).replace("{k}", String.valueOf(viol.size() - 3))));
            if (!ok.isEmpty() && R.nextInt(100) < 45)
                pages.addAll(Lines.pages(Lines.pick(Lines.GOOD_NOTE).replace("{r}", ok.get(R.nextInt(ok.size())).reveal())));
            int[] secs = new int[puns.size()];
            for (int i = 0; i < puns.size(); i++) {
                secs[i] = (int) (base * (0.8 + R.nextDouble() * 0.5));
                pages.addAll(Lines.pages(puns.get(i).say().replace("{T}", String.valueOf(secs[i]))));
            }
            int scene = R.nextInt(3);
            if (sev == 3) pages.addAll(Lines.pages(Lines.pick(scene == 0 ? Lines.SCENE_CAT : scene == 1 ? Lines.SCENE_KID : Lines.SCENE_SHEEP)));
            if (satanThisVisit && R.nextInt(100) < 50) {
                pages.add(SATAN + Lines.wrap("[из-под земли] Держи и от меня подарочек, бог тебе не даст.", 26));
                cmd(server, p, "run give @s minecraft:coal[custom_name=\"Сатанинский уголь\"] 3");
            }

            int t = say(List.of(p), pages, mood);
            freezeWorld(server, List.of(p), t);
            longest = Math.max(longest, t + 260);
            final int fSev = sev, fScore = score;
            schedule(t, () -> act(server, p, fSev, scene, fScore, puns, secs));
        }
        if (anyBad) { if (++badStreak >= 2) { forceWrath = true; badStreak = 0; } } else badStreak = 0;
        schedule(longest, () -> {
            unfreezeWorld(server);
            sendFx(server.getPlayerList().getPlayers(), "hand", "0", 0);
            busy = false;
            nextVisit = tick + interval();
            sendTimer(server, (int) (nextVisit - tick));
        });
    }

    // ---------- ДЕЙСТВИЯ ----------
    static void applyPun(MinecraftServer server, ServerPlayer p, Puns.Pun pun, int secs) {
        if (!online(server, p)) return;
        String T = String.valueOf(secs);
        for (String c : pun.cmds()) cmd(server, p, c.replace("{T}", T));
        if (pun.fx() != null) {
            String[] f = pun.fx().split(":");
            ServerPlayNetworking.send(p, new FxPayload(f[0], f.length > 1 ? f[1] : "", secs * 20));
        }
        if (pun.special() != null) {
            for (String sp : pun.special().split(",")) {
                if (sp.equals("mute")) MUTED.put(p.getUUID(), tick + secs * 20L);
                else if (sp.equals("twitch")) twitch(server, p, tick + secs * 20L);
            }
        }
        for (String u : pun.undo()) schedule(secs * 20, () -> { if (online(server, p)) cmd(server, p, u); });
        note(p, "§c[Наказание] §f" + pun.name() + " (" + secs + " с)");
    }

    static void twitch(MinecraftServer server, ServerPlayer p, long end) {
        if (tick >= end || !online(server, p)) return;
        cmd(server, p, "run tp @s ~ ~ ~ ~" + (R.nextInt(61) - 30) + " ~" + (R.nextInt(31) - 15));
        schedule(15, () -> twitch(server, p, end));
    }

    static void reward(MinecraftServer s, ServerPlayer p, int n) {
        for (int i = 0; i < n; i++) {
            String[] r = Puns.GIFTS[R.nextInt(Puns.GIFTS.length)];
            for (int k = 1; k < r.length; k++) cmd(s, p, r[k]);
            note(p, "§e[Бог] §fНаграда: " + r[0]);
        }
    }

    static void act(MinecraftServer s, ServerPlayer p, int sev, int scene, int score, List<Puns.Pun> puns, int[] secs) {
        if (!online(s, p)) return;
        if (score >= 1) reward(s, p, 1 + (score >= 3 ? 1 : 0) + (score >= 6 ? 1 : 0));
        else if (score == 0 && R.nextInt(100) < 30) {
            cmd(s, p, "run summon minecraft:bee ~8 ~1 ~ {CustomName:\"Пчела с востока\"}");
            note(p, "§7[Бог] §fОн прислал пчелу. Просто так.");
        }
        for (int i = 0; i < puns.size(); i++) applyPun(s, p, puns.get(i), secs[i]);
        if (sev == 3) wrathScene(s, p, scene);
    }

    static void wrathScene(MinecraftServer s, ServerPlayer p, int scene) {
        cmd(s, p, "run weather thunder");
        cmd(s, p, "run effect give @s minecraft:darkness 15 0 true");
        if (scene == 0) { // кошка
            cmd(s, p, "unless entity @e[type=minecraft:cat,distance=..30] run summon minecraft:cat ~2 ~ ~ {CustomName:\"Мурзик\"}");
            schedule(70, () -> {
                String sel = "@e[type=minecraft:cat,limit=1,sort=nearest,distance=..30]";
                cmd(s, p, "as " + sel + " at @s run particle minecraft:explosion_emitter ~ ~1 ~");
                cmd(s, p, "as " + sel + " at @s run playsound minecraft:entity.generic.explode hostile @a ~ ~ ~ 2");
                cmd(s, p, "run kill " + sel);
            });
        } else if (scene == 1) { // ребёнок-житель
            cmd(s, p, "run summon minecraft:villager ~2 ~ ~ {Age:-24000,CustomName:\"Малыш Тимми\"}");
            schedule(80, () -> {
                String sel = "@e[type=minecraft:villager,name=\"Малыш Тимми\",limit=1,sort=nearest,distance=..30]";
                cmd(s, p, "as " + sel + " at @s run particle minecraft:explosion_emitter ~ ~1 ~");
                cmd(s, p, "as " + sel + " at @s run playsound minecraft:entity.generic.explode hostile @a ~ ~ ~ 2");
                cmd(s, p, "run kill " + sel);
            });
        } else { // адское очищение от овец
            schedule(40, () -> {
                cmd(s, p, "as @e[type=minecraft:sheep,distance=..40] at @s run particle minecraft:explosion_emitter ~ ~1 ~");
                cmd(s, p, "as @e[type=minecraft:sheep,distance=..40] at @s run playsound minecraft:entity.generic.explode hostile @a ~ ~ ~ 1");
                cmd(s, p, "run kill @e[type=minecraft:sheep,distance=..40]");
            });
        }
        for (int i = 0; i < 3; i++) schedule(30 + i * 25, () -> cmd(s, p, "run summon minecraft:lightning_bolt ~ ~ ~"));
    }
}
