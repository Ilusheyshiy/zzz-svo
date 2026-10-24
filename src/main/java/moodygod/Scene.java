package moodygod;

import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Сцена «Кларк и Вкусно и точка» (4-й визит, один раз). Платформа из дерна остаётся в мире для будущих сцен. */
final class Scene {
    static boolean active, done, lock;
    static int cx, cz;
    static final int PY = 220;
    static float yawNow, yawTarget;
    static final Map<UUID, double[]> BACK = new HashMap<>();
    static List<ServerPlayer> actors = new ArrayList<>();

    static final String[] MONO = {
        "Так. Присаживайтесь. Я хочу поговорить про Вкусно и точка.",
        "Это не еда. Это хрустящая катастрофа в бумажном пакете.",
        "Картошка там как мои надежды: холодная и в трёх граммах.",
        "Вчера мне продали бургер. Один бургер! А булка там как мои обещания: мягкая и пустая.",
        "А самое страшное — это когда приносят нагетсы, а внутри у них ВНУТРИ у них"
    };

    // G — реплика Бога, C/M/K — Кларк/Мэри Кляйн/Повар, «!» — злится, «~» — перебивается, CAM — камера (градусы), SHOW — спрайты
    static final String[] SCRIPT = {
        "CAM|90", "SHOW|C,M",
        "M~|Я не понимаю, что вы от меня хотите...",
        "C!|Я архитектор бЛяттьб… Я хочу построить Вкусно и точка рядом со своим домом, чтобы он работал 24/7 без перебоев!",
        "M|Это невозможно.", "C|Нет, возможно!", "M|Нет!", "C|Нет, возможно!",
        "M|Докажите, что вы архитектор!", "C|Я архитектор!", "M|Где ваш бейджик?", "C|У тебя в жопе!",
        "M|Оу, мы дошли до БДСМ.", "C|Нет, я хочу Вкусно и точка!",
        "M|Это невозможно!", "C|Нет, возможно!", "M|Нет!", "C|Нет, возможно!",
        "M!|СУЙ!!!", "C|Что совать?", "WAIT|30", "M|Ананас в жопу.", "C|Ладно.",
        "SHOW|", "CAM|0", "WAIT|10", "G|Че за хуйня?..",
        "CAM|90", "SHOW|C,M",
        "C|Я хочу Вкусно и точка, понимаете?", "M|Понимаю.",
        "C|Я уже придумал план, как я буду строить это, как это… хэ-хэ, понимаете? Находится с моим домом и всякое прочее.",
        "M|Вы будете единственным посетителем в этом заведении?",
        "C|Эу… ну, как бы, да, я так и планировал…", "WAIT|16",
        "C|В чём проблема? Здание 125 квадратных метров мне хватит.", "M|Сколько, простите?",
        "C|Сто… двадцать пять квадратных метров.", "M|Но у вас квартира восемь на восемь… Это безуми-",
        "C!|Я АРХИТЕКТОР, БЛЯТЬ! Я РАСПЛАНИРОВАЛ ТАК, ЧТОБЫ ВКУСНО И ТОЧКА ВЫХОДИЛ К ДОМУ МОЕГО СОСЕДА, КОТОРОГО ВЫСЕЛЯТ, И ОН БУДЕТ ЖИТЬ НА УЛИЦЕ!",
        "WAIT|24", "M|Кто у вас будет работать?", "C|Мой папа.", "M|Можно с ним поговорить?",
        "SHOW|C,M,K",
        "K!|ААА, ОГУЗОК! ДОРМОЕД ТЫ ТУПОЙ, ЧТО ТЫ НАДЕЛАЛ? ПРИДУРОК! ТЕБЕ БЫЛО СКАЗАНО ЗАПИХНУТЬ АНАНАС В ЖОПУ, НАХРЕНА ТЫ В МОЮ-ТО ЕГО ЗАПИХНУЛ?",
        "C!|Я НЕ ХОТЕЛ, ПАПА!", "WAIT|30", "END|"
    };

    static void start(MinecraftServer server, List<ServerPlayer> ps) {
        active = true; lock = false; actors = ps; yawNow = yawTarget = 0;
        cx = ps.get(0).blockPosition().getX();
        cz = ps.get(0).blockPosition().getZ();
        BACK.clear();
        for (ServerPlayer p : ps) BACK.put(p.getUUID(), new double[]{p.getX(), p.getY(), p.getZ(), p.getYRot(), p.getXRot()});
        GodMod.serverCmd(server, "execute in minecraft:overworld run forceload add " + (cx - 6) + " " + (cz - 6) + " " + (cx + 6) + " " + (cz + 6));
        int t1 = GodMod.say(ps, Lines.pages("Так. Идите сюда. Поговорим на свежем воздухе. Очень свежем."), 0);
        GodMod.schedule(t1, () -> stage(server, ps));
    }

    static void stage(MinecraftServer server, List<ServerPlayer> ps) {
        GodMod.serverCmd(server, "execute in minecraft:overworld run fill " + (cx - 5) + " 219 " + (cz - 5) + " " + (cx + 5) + " 219 " + (cz + 5) + " minecraft:grass_block");
        GodMod.serverCmd(server, "execute in minecraft:overworld run setblock " + (cx + 4) + " 220 " + cz + " minecraft:spruce_stairs[facing=west]");
        GodMod.schedule(25, () -> {
            lock = true;
            int i = 0;
            for (ServerPlayer p : ps) {
                GodMod.cmd(server, p, "run effect give @s minecraft:resistance 900 4 true");
                GodMod.cmd(server, p, String.format(Locale.ROOT, "run tp @s %.1f %d %.1f 0 0", cx + 0.5 - i * 1.5, PY, cz + 0.5));
                i++;
            }
            GodMod.sendFx(ps, "scene_on", "", 0);
            GodMod.schedule(30, () -> monologue(server, ps));
        });
    }

    static void lockTick(MinecraftServer server) {
        if (Math.abs(yawTarget - yawNow) <= 9f) yawNow = yawTarget;
        else yawNow += Math.signum(yawTarget - yawNow) * 9f;
        int i = 0;
        for (ServerPlayer p : actors) {
            if (GodMod.online(server, p))
                GodMod.cmd(server, p, String.format(Locale.ROOT, "run tp @s %.1f %d %.1f %.1f 0", cx + 0.5 - i * 1.5, PY, cz + 0.5, yawNow));
            i++;
        }
    }

    static void monologue(MinecraftServer server, List<ServerPlayer> ps) {
        List<String> pages = new ArrayList<>();
        for (String s : MONO) pages.add(Lines.wrap(s, 26));
        int priorMs = 0;
        for (int i = 0; i < pages.size() - 1; i++) priorMs += GodPayload.pageMs(pages.get(i));
        GodMod.say(ps, pages, 0);
        int last = pages.get(pages.size() - 1).length();
        int freezeAt = (priorMs + 700 + (int) (last * 0.5 * GodPayload.MS_PER_CHAR)) / 50;
        GodMod.schedule(freezeAt, () -> {
            GodMod.sendFx(ps, "freeze", "", 0);
            GodMod.schedule(25, () -> script(server, ps));
        });
    }

    static void script(MinecraftServer server, List<ServerPlayer> ps) {
        int t = 0;
        for (String b : SCRIPT) {
            String[] kv = b.split("\\|", 2);
            final String k = kv[0];
            final String v = kv.length > 1 ? kv[1] : "";
            final int at = t;
            if (k.equals("CAM")) {
                final int deg = Integer.parseInt(v);
                GodMod.schedule(at, () -> yawTarget = deg);
                t += 14;
            } else if (k.equals("WAIT")) {
                t += Integer.parseInt(v);
            } else if (k.equals("SHOW")) {
                GodMod.schedule(at, () -> GodMod.sendFx(ps, "show", v, 0));
            } else if (k.equals("G")) {
                final String page = Lines.wrap(v, 26);
                GodMod.schedule(at, () -> {
                    GodMod.sendFx(ps, "unfreeze", "", 0);
                    GodMod.schedule(40, () -> {
                        GodMod.say(ps, List.of(page), 0);
                        GodMod.schedule((page.length() * GodPayload.MS_PER_CHAR) / 50 + 6, () -> GodMod.sendFx(ps, "freeze", "", 0));
                    });
                });
                t += 40 + (page.length() * GodPayload.MS_PER_CHAR) / 50 + 40;
            } else if (k.equals("END")) {
                GodMod.schedule(at + 10, () -> end(server, ps));
            } else { // реплика персонажа: C / M / K, суффикс ! или ~
                final char who = k.charAt(0);
                final boolean angry = k.indexOf('!') > 0;
                final boolean cut = k.indexOf('~') > 0;
                final int ms = 450 + v.length() * (angry ? 26 : 36) + 500;
                GodMod.schedule(at, () -> GodMod.sendFx(ps, "say", who + (angry ? "!" : ".") + v, ms));
                t += (int) (ms * (cut ? 0.75 : 1.0) / 50) + 10;
            }
        }
    }

    static void end(MinecraftServer server, List<ServerPlayer> ps) {
        GodMod.sendFx(ps, "scene_off", "", 0);
        lock = false;
        yawTarget = 0;
        for (ServerPlayer p : ps) {
            double[] b = BACK.get(p.getUUID());
            if (b != null && GodMod.online(server, p)) {
                GodMod.cmd(server, p, String.format(Locale.ROOT, "run tp @s %.2f %.2f %.2f %.1f %.1f", b[0], b[1], b[2], b[3], b[4]));
                GodMod.cmd(server, p, "run effect clear @s minecraft:resistance");
            }
        }
        GodMod.schedule(40, () -> {
            GodMod.serverCmd(server, "execute in minecraft:overworld run forceload remove " + (cx - 6) + " " + (cz - 6) + " " + (cx + 6) + " " + (cz + 6));
            active = false; done = true;
            GodMod.busy = false;
            GodMod.nextVisit = GodMod.tick + GodMod.interval();
            GodMod.sendTimer(server, (int) (GodMod.nextVisit - GodMod.tick));
        });
    }
}
