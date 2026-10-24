package moodygod.client;

import java.util.*;
import moodygod.GodPayload;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/** Текст Бога (печать → падение → мини-взрыв), небо-оверлей, рука с неба, диско, сцена с Кларком, эффекты наказаний. */
public final class GodHud {
    record Pending(String page, int mood, boolean satan) {}
    static final class Letter { char c; float x, y; }
    static final class Particle { float x, y, vx, vy; long born; int life, color; }

    static final ArrayDeque<Pending> queue = new ArrayDeque<>();
    static final List<Letter> letters = new ArrayList<>();
    static final List<Particle> parts = new ArrayList<>();
    static final Random R = new Random();
    static final int[] SCHIZO = {0xFFFF2A2A, 0xFF33FF66, 0xFFFFFFFF, 0xFFB050FF, 0xFFFFE94A, 0xFF00E5FF};
    static final int[] DISCO = {0xFFFF2A2A, 0xFF33FF66, 0xFF3399FF, 0xFFFFE94A, 0xFFFF33CC, 0xFF00E5FF, 0xFFFF9900};
    static final int[] MOOD_COL = {0xFFFF8FC8, 0xFFFFE94A, 0xFF8FA3BF, 0, 0xFFFF2A2A, 0xFFFFC0E0, 0xFFB0B0B0, 0xFFE0E0A0,
        0xFFFF66FF, 0xFFD8B08C, 0xFFFFD700, 0xFF66FF66, 0xFFFFB347, 0xFF00E5FF, 0xFFCCCCCC};
    static final float[] MOOD_PITCH = {1.5f, 1.9f, 0.7f, 1f, 0.6f, 1.6f, 0.9f, 1.2f, 1.7f, 1.0f, 1.3f, 1.4f, 0.8f, 1.1f, 2.0f};

    static SoundEvent snd(String id) { return SoundEvent.createVariableRangeEvent(Identifier.withDefaultNamespace(id)); }
    static final SoundEvent SND_TYPE = snd("block.note_block.bit");
    static final SoundEvent SND_SATAN = snd("block.note_block.basedrum");
    static final SoundEvent SND_BASS = snd("block.note_block.bass");
    static final SoundEvent SND_BOOM = snd("entity.generic.explode");
    static final SoundEvent SND_ARRIVE = snd("block.beacon.activate");

    static Pending cur;
    static int phase, typed, landedCount, curMood;
    static boolean[] landed = new boolean[0];
    static long phaseStart, lastNow, timerDeadline = -1;
    static boolean visitNow, frozen;
    static float dim, scale = 2f, sky, glow, hand;
    static long handUntil, discoUntil, eyeUntil, deafUntil;
    static char eyeSide = 'B';
    // сцена
    static boolean sceneOn;
    static final Set<Character> shown = new HashSet<>();
    static long showAt;
    static char sayWho = ' ';
    static boolean sayAngry;
    static String sayText = "";
    static long sayStart;
    static int sayTyped;

    // ================= приём пакетов =================
    public static void setTimer(int ticks) {
        if (ticks < 0) { visitNow = true; timerDeadline = -1; }
        else { visitNow = false; timerDeadline = System.currentTimeMillis() + ticks * 50L; }
    }

    public static void enqueue(String text, int mood) {
        for (String pg : text.split("\f"))
            queue.add(new Pending(GodPayload.strip(pg), GodPayload.moodOf(pg, mood), GodPayload.isSatan(pg)));
    }

    public static void fx(String op, String a, int n) {
        long now = System.currentTimeMillis();
        switch (op) {
            case "disco" -> discoUntil = now + n * 50L;
            case "disco_end" -> discoUntil = 0;
            case "eye" -> { eyeSide = a.isEmpty() ? 'B' : a.charAt(0); eyeUntil = now + n * 50L; }
            case "deaf" -> deafUntil = now + n * 50L;
            case "hand" -> handUntil = a.equals("1") ? now + 1600 : 0;
            case "interrupt" -> interrupt();
            case "freeze" -> { frozen = true; dualType(); }
            case "unfreeze" -> { frozen = false; if (cur != null) { phase = 3; phaseStart = now; typed = letters.size(); } }
            case "scene_on" -> { sceneOn = true; shown.clear(); sayText = ""; }
            case "scene_off" -> { sceneOn = false; frozen = false; shown.clear(); sayText = ""; }
            case "show" -> {
                Set<Character> ns = new HashSet<>();
                for (String s : a.split(",")) if (!s.isEmpty()) ns.add(s.charAt(0));
                if (ns.contains('K') && !shown.contains('K')) showAt = now;
                shown.clear(); shown.addAll(ns);
            }
            case "say" -> {
                if (a.length() >= 2) {
                    sayWho = a.charAt(0); sayAngry = a.charAt(1) == '!'; sayText = a.substring(2);
                    sayStart = now; sayTyped = 0;
                }
            }
            default -> { }
        }
    }

    // ================= звук =================
    static void play(SoundEvent s, float pitch, float vol) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(s, pitch, vol));
    }
    static void dualType() { play(SND_BASS, 0.5f, 0.6f); play(SND_TYPE, 1.9f, 0.5f); }

    static int moodColor(int m, long now) {
        if (m == 3) return SCHIZO[(int) ((now / 70) % SCHIZO.length)];
        return MOOD_COL[Math.max(0, Math.min(14, m))];
    }
    static float typePitch(int m) {
        float p = MOOD_PITCH[Math.max(0, Math.min(14, m))];
        if (m == 3) return 0.5f + R.nextFloat() * 1.5f;
        if (m == 12) return p + (R.nextFloat() - 0.5f) * 0.6f;
        return p + R.nextFloat() * 0.1f;
    }

    static void drawLetter(GuiGraphicsExtractor g, Font f, char c, float x, float y, float s, int color) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(s, s);
        g.text(f, String.valueOf(c), 0, 0, color);
        g.pose().popMatrix();
    }

    // ================= таймер =================
    static void drawTimer(GuiGraphicsExtractor g, Minecraft mc, long now, int W) {
        String s; int col = 0xFFFFFFFF;
        if (cur != null || visitNow || !queue.isEmpty()) { s = "БОГ ЗДЕСЬ"; col = 0xFFFFE94A; }
        else if (timerDeadline < 0) return;
        else {
            long ms = Math.max(0, timerDeadline - now);
            long sec = ms / 1000;
            s = "Визит Бога через " + (sec / 60) + ":" + String.format("%02d", sec % 60);
            if (ms < 10000 && (now / 400) % 2 == 0) col = 0xFFFF4040;
        }
        int w = mc.font.width(s);
        int x = (W - w) / 2;
        g.fill(x - 4, 2, x + w + 4, 14, 0x77000000);
        g.text(mc.font, s, x, 4, col);
    }

    // ================= небо, рука, диско, глаза =================
    static void drawSky(GuiGraphicsExtractor g, int W, int H, long now, float dts) {
        boolean god = visitNow || cur != null || !queue.isEmpty();
        boolean satanNow = cur != null && cur.satan();
        sky += ((!god ? 0f : satanNow ? 0.45f : 1f) - sky) * Math.min(1f, dts * 1.5f);
        glow += ((satanNow ? 1f : 0f) - glow) * Math.min(1f, dts * 3f);
        int bands = 28;
        if (sky > 0.01f) {
            int rgb = curMood == 4 ? 0xFF5A00 : 0xFFD400;
            float pulse = 0.9f + 0.1f * (float) Math.sin(now / 400.0);
            int hh = (int) (H * 0.7f);
            for (int i = 0; i < bands; i++) {
                int a = (int) (150 * sky * pulse * (1f - (float) i / bands));
                g.fill(0, hh * i / bands, W, hh * (i + 1) / bands, (a << 24) | rgb);
            }
            g.fill(0, 0, W, H, ((int) (30 * sky) << 24) | rgb);
        }
        if (glow > 0.01f) {
            int hh = (int) (H * 0.45f);
            for (int i = 0; i < bands; i++) {
                int a = (int) (170 * glow * (1f - (float) i / bands));
                g.fill(0, H - hh * (i + 1) / bands, W, H - hh * i / bands, (a << 24) | 0x8B0000);
            }
        }
    }

    // Рука с неба: начала не видно, 3 кадра с разным контуром, смена каждые 0.5 с.
    static final int[][] FING = {{8, 11, 9, 6}, {9, 10, 11, 5}, {7, 12, 8, 7}};
    static final int[] THUMB = {5, 6, 4};
    static final int[][] JIT = {{0, 0}, {1, 0}, {0, 1}};
    static void rect(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int c) { g.fill(x1, Math.max(0, y1), x2, y2, c); }

    static void drawHand(GuiGraphicsExtractor g, int W, int H, long now, float dts) {
        boolean on = now < handUntil && !sceneOn;
        hand += ((on ? 1f : 0f) - hand) * Math.min(1f, dts * 2f);
        if (hand < 0.02f) return;
        int fr = (int) ((now / 500) % 3);
        int cell = Math.max(3, H / 55);
        int a = (int) (255 * hand);
        int ol = (a << 24) | 0x2A1410, sk = (a << 24) | 0xD8C7B5, sh = (a << 24) | 0xB09C88;
        int cx = (int) (W * 0.62) + JIT[fr][0] * cell;
        int armW = 6 * cell, palmW = 15 * cell;
        int bottomArm = (int) (H * 0.14) + JIT[fr][1] * cell;
        int o = Math.max(1, cell / 2 + (fr == 1 ? 1 : 0)); // толщина контура тоже «дрожит»
        // рука (уходит за верх экрана)
        rect(g, cx - armW / 2 - o, 0, cx + armW / 2 + o, bottomArm, ol);
        rect(g, cx - armW / 2, 0, cx + armW / 2, bottomArm, sk);
        rect(g, cx + armW / 2 - cell, 0, cx + armW / 2, bottomArm, sh);
        // ладонь
        int pt = bottomArm, pb = bottomArm + 12 * cell;
        rect(g, cx - palmW / 2 - o, pt, cx + palmW / 2 + o, pb + o, ol);
        rect(g, cx - palmW / 2, pt, cx + palmW / 2, pb, sk);
        rect(g, cx + palmW / 2 - cell, pt, cx + palmW / 2, pb, sh);
        // большой палец
        int tl = THUMB[fr] * cell;
        rect(g, cx - palmW / 2 - 3 * cell - o, pt + 2 * cell - o, cx - palmW / 2 + o, pt + 2 * cell + tl + o, ol);
        rect(g, cx - palmW / 2 - 3 * cell, pt + 2 * cell, cx - palmW / 2, pt + 2 * cell + tl, sk);
        // четыре пальца свисают
        int fw = 3 * cell;
        for (int i = 0; i < 4; i++) {
            int fx0 = cx - palmW / 2 + i * (fw + cell);
            int len = FING[fr][i] * cell;
            rect(g, fx0 - o, pb - cell, fx0 + fw + o, pb + len + o, ol);
            rect(g, fx0, pb - cell, fx0 + fw, pb + len, sk);
            rect(g, fx0 + fw - cell / 2, pb - cell, fx0 + fw, pb + len, sh);
        }
    }

    static void drawDisco(GuiGraphicsExtractor g, int W, int H, long now) {
        if (now >= discoUntil) return;
        int c = DISCO[(int) ((now / 130) % DISCO.length)];
        g.fill(0, 0, W, H, (0x40 << 24) | (c & 0xFFFFFF));
        for (int k = 0; k < 5; k++) {
            int x = (int) ((now / 5 + k * W / 5) % W);
            g.fill(x, 0, x + 8, H, (0x30 << 24) | (DISCO[(k + (int) (now / 200)) % DISCO.length] & 0xFFFFFF));
        }
    }

    static void drawEyes(GuiGraphicsExtractor g, int W, int H, long now) {
        if (now >= eyeUntil) return;
        int c = 0xFF000000;
        if (eyeSide == 'L' || eyeSide == 'B') g.fill(0, 0, W / 2, H, c);
        if (eyeSide == 'R' || eyeSide == 'B') g.fill(W / 2, 0, W, H, c);
    }

    // ================= текст Бога =================
    static void start(Minecraft mc, Pending p, long now, int W, int H) {
        boolean first = cur == null && dim < 0.05f;
        cur = p;
        curMood = p.mood();
        letters.clear();
        scale = p.satan() ? 1.5f : 2f;
        String[] lines = p.page().split("\n");
        float lineH = 11 * scale;
        float top = p.satan() ? H * 0.70f : H * 0.28f;
        for (int li = 0; li < lines.length; li++) {
            float sx = (W - mc.font.width(lines[li]) * scale) / 2f;
            for (int ci = 0; ci < lines[li].length(); ci++) {
                Letter l = new Letter();
                l.c = lines[li].charAt(ci);
                l.x = sx + mc.font.width(lines[li].substring(0, ci)) * scale;
                l.y = top + li * lineH;
                letters.add(l);
            }
        }
        landed = new boolean[letters.size()];
        landedCount = 0; typed = 0; phase = 1; phaseStart = now;
        if (first && !p.satan()) play(SND_ARRIVE, 0.5f, 0.8f);
        if (p.satan()) play(SND_SATAN, 0.4f, 0.5f);
    }

    /** Перебивание: на месте последней напечатанной буквы остаются три точки. */
    static void interrupt() {
        if (cur == null || phase != 1) return;
        Minecraft mc = Minecraft.getInstance();
        int n = Math.min(typed, letters.size());
        StringBuilder sb = new StringBuilder();
        int cnt = 0;
        for (char ch : cur.page().toCharArray()) {
            if (ch == '\n') { sb.append(ch); continue; }
            if (cnt >= n) break;
            sb.append(ch); cnt++;
        }
        sb.append("...");
        long now = System.currentTimeMillis();
        start(mc, new Pending(sb.toString(), cur.mood(), cur.satan()), now,
            mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
        typed = letters.size();
        phase = 2; phaseStart = now; frozen = false;
        play(SND_BOOM, 0.6f, 0.4f);
    }

    static void spawnBoom(float x, float y, int mood, long now) {
        for (int i = 0; i < 9; i++) {
            Particle q = new Particle();
            q.x = x; q.y = y;
            q.vx = (R.nextFloat() - 0.5f) * 280f;
            q.vy = -60f - R.nextFloat() * 220f;
            q.born = now; q.life = 350 + R.nextInt(300);
            int[] pal = {0xFFFFAA00, 0xFFFF5500, 0xFFFFFFFF, moodColor(mood, now)};
            q.color = pal[R.nextInt(pal.length)];
            parts.add(q);
        }
    }

    // ================= сцена: спрайты =================
    static final String[] CLARK = {
        "....HHHH....", "...HHHHHH...", "...SSSSSS...", "...SeSSeS...", "...SSSSSS...", "....SmmS....", ".....SS.....",
        "..JJJWWJJJ..", ".JJJJWTJJJJ.", ".JJJJJTJJJJ.", ".JJJJJTJJJJ.", ".SJJJJJJJJS.", ".SJJJJJJJJS.", "..JJJJJJJJ..",
        "..JJJJJJJJ..", "...PPPPPP...", "...PPPPPP...", "...PP..PP...", "...PP..PP...", "...PP..PP...", "..BBB..BBB..", "..BBB..BBB.."};
    static final String[] MARY = {
        "AA.HHHHHH.AA", "AA.HHHHHH.AA", "AA.SSSSSS.AA", "AA.SeSSeS.AA", "AA.SSSSSS.AA", "AA..SmmS..AA",
        "AAWWWWWWWWAA", "AAWWWWWWWWAA", "AAWWWWWWWWAA", "AAWWWWWWWWAA", "AAAPPPPPPAAA", "AAAPPPPPPAAA",
        "AAAAAAAAAAAA", "AAAAAAAAAAAA", ".LL......LL."};
    static final String[] COOK = {
        "...WWWWWW...", "..WWWWWWWW..", "..WWWWWWWW..", "...WWWWWW...", "...RRRRRR...", "...ReRReR...", "...RRRRRR...",
        "...KKKKKK...", "....RRRR....", "..WWWWWWWW..", ".WWWWWWWWWW.", ".RWWWWWWWWR.", ".RWWWAAWWWR.", "..WWAAAAWW..",
        "..WWWWWWWW..", "...PPPPPP...", "...PP..PP...", "...PP..PP...", "...PP..PP...", "...PP..PP...", "..BBB..BBB..", "..BBB..BBB.."};

    static int pal(char who, char c) {
        if (who == 'C') return switch (c) {
            case 'H' -> 0xFF1A1A1A; case 'S' -> 0xFF6B4A32; case 'e' -> 0xFFFFFFFF; case 'm' -> 0xFF3A2418; case 'J' -> 0xFF2D3A5A;
            case 'W' -> 0xFFFFFFFF; case 'T' -> 0xFF8B0000; case 'P' -> 0xFF222222; case 'B' -> 0xFF111111; default -> 0; };
        if (who == 'M') return switch (c) {
            case 'A' -> 0xFF7A1F1F; case 'H' -> 0xFF5A3A1A; case 'S' -> 0xFFE0B090; case 'e' -> 0xFF222222; case 'm' -> 0xFFB06060;
            case 'W' -> 0xFFF4F4F4; case 'P' -> 0xFF445577; case 'L' -> 0xFF3A2A1A; default -> 0; };
        return switch (c) {
            case 'W' -> 0xFFFFFFFF; case 'R' -> 0xFFD98A6A; case 'e' -> 0xFF111111; case 'K' -> 0xFF222222; case 'A' -> 0xFFBBBBBB;
            case 'P' -> 0xFF333355; case 'B' -> 0xFF111111; default -> 0; };
    }

    static void drawSprite(GuiGraphicsExtractor g, String[] rows, char who, int cx, int yBottom, int cell, int ox, int oy, float rise) {
        int h = rows.length, w = rows[0].length();
        int x0 = cx - w * cell / 2 + ox;
        int y0 = yBottom - h * cell + oy + (int) (rise * h * cell);
        for (int r = 0; r < h; r++)
            for (int c = 0; c < w; c++) {
                int col = pal(who, rows[r].charAt(c));
                if (col != 0) g.fill(x0 + c * cell, y0 + r * cell, x0 + (c + 1) * cell, y0 + (r + 1) * cell, col);
            }
    }

    static void drawScene(GuiGraphicsExtractor g, Minecraft mc, int W, int H, long now) {
        if (!sceneOn || shown.isEmpty() || frozen && cur != null && !shown.contains('C')) return;
        int cell = Math.max(3, H / 60);
        int base = (int) (H * 0.88);
        int bob = (int) (Math.sin(now / 300.0) * 2);
        record Spot(char who, String[] rows, int x, String name) {}
        Spot[] spots = {new Spot('C', CLARK, (int) (W * 0.34), "Кларк"), new Spot('M', MARY, (int) (W * 0.62), "Др. Мэри Кляйн"),
            new Spot('K', COOK, (int) (W * 0.82), "Повар")};
        for (Spot s : spots) {
            if (!shown.contains(s.who())) continue;
            boolean speaking = sayWho == s.who() && !sayText.isEmpty();
            int ox = 0, oy = bob;
            if (speaking && sayAngry) { ox = R.nextInt(5) - 2; oy += R.nextInt(5) - 2; }
            float rise = 0f;
            if (s.who() == 'K') rise = Math.max(0f, 1f - (now - showAt) / 500f);
            drawSprite(g, s.rows(), s.who(), s.x(), base, cell, ox, oy, rise);
            int top = base - s.rows().length * cell;
            g.text(mc.font, s.name(), s.x() - mc.font.width(s.name()) / 2, top - 12, 0xFFFFE94A);
            if (speaking) {
                int per = sayAngry ? 26 : 36;
                int tc = Math.min(sayText.length(), (int) ((now - sayStart) / per));
                for (int k = sayTyped; k < tc; k++) {
                    char ch = sayText.charAt(k);
                    if (ch == ' ') continue;
                    if (s.who() == 'C') { play(SND_BASS, sayAngry ? 0.4f : 0.55f, 0.5f); play(SND_TYPE, 1.9f, 0.35f); }
                    else if (s.who() == 'M') play(SND_TYPE, 1.5f, 0.45f);
                    else play(SND_BASS, 0.45f, 0.7f);
                }
                sayTyped = tc;
                String shownText = sayText.substring(0, tc);
                List<String> ls = new ArrayList<>();
                StringBuilder ln = new StringBuilder();
                for (String w : shownText.split(" ", -1)) {
                    if (ln.length() > 0 && ln.length() + 1 + w.length() > 30) { ls.add(ln.toString()); ln.setLength(0); }
                    if (ln.length() > 0) ln.append(' ');
                    ln.append(w);
                }
                ls.add(ln.toString());
                int ty = top - 18 - ls.size() * 11;
                int maxw = 0;
                for (String l2 : ls) maxw = Math.max(maxw, mc.font.width(l2));
                int bx = Math.max(4, Math.min(W - maxw - 12, s.x() - maxw / 2 - 4));
                g.fill(bx - 4, ty - 3, bx + maxw + 8, ty + ls.size() * 11 + 2, 0xAA000000);
                for (int i = 0; i < ls.size(); i++)
                    g.text(mc.font, ls.get(i), bx, ty + i * 11, sayAngry ? 0xFFFF5555 : 0xFFFFFFFF);
            }
        }
    }

    // ================= основной рендер =================
    public static void render(GuiGraphicsExtractor g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        long now = System.currentTimeMillis();
        int W = mc.getWindow().getGuiScaledWidth(), H = mc.getWindow().getGuiScaledHeight();
        float dts = Math.min(0.1f, (now - lastNow) / 1000f);
        lastNow = now;

        drawSky(g, W, H, now, dts);
        drawDisco(g, W, H, now);
        drawHand(g, W, H, now, dts);
        drawTimer(g, mc, now, W);
        if (cur == null && !queue.isEmpty()) start(mc, queue.poll(), now, W, H);

        boolean busy = cur != null || !queue.isEmpty();
        float target = !busy ? 0f : (cur != null && cur.satan() ? 0.3f : 0.5f);
        dim += (target - dim) * Math.min(1f, dts * 5f);
        if (dim > 0.01f) {
            g.fill(0, 0, W, H, ((int) (dim * 255)) << 24);
            if (cur != null && cur.mood() == 4 && !cur.satan()) {
                int a = (int) (20 + 20 * Math.sin(now / 90.0));
                g.fill(0, 0, W, H, (a << 24) | 0xFF0000);
            }
        }

        if (cur != null) tickPage(g, mc, now, H);

        Iterator<Particle> it = parts.iterator();
        while (it.hasNext()) {
            Particle q = it.next();
            long age = now - q.born;
            if (age > q.life) { it.remove(); continue; }
            q.vy += 700f * dts; q.x += q.vx * dts; q.y += q.vy * dts;
            int a = (int) (255 * (1f - (float) age / q.life));
            int sz = 2 + (int) (2 * (1f - (float) age / q.life));
            g.fill((int) q.x, (int) q.y, (int) q.x + sz, (int) q.y + sz, (a << 24) | (q.color & 0xFFFFFF));
        }

        drawScene(g, mc, W, H, now);
        drawEyes(g, W, H, now);
        if (now < deafUntil) {
            mc.getSoundManager().stop();
            g.text(mc.font, "[ВЫ ОГЛОХЛИ]", 6, H - 14, 0xFFAAAAAA);
        }
    }

    static void tickPage(GuiGraphicsExtractor g, Minecraft mc, long now, int H) {
        int n = letters.size();
        long t = now - phaseStart;
        boolean satan = cur.satan();
        int mood = cur.mood();
        float amp = satan ? 1.5f : mood == 4 ? 2.5f : mood == 3 ? 1f : 0f;
        float ox = (R.nextFloat() - 0.5f) * amp, oy = (R.nextFloat() - 0.5f) * amp;
        int base = satan ? 0x7A0A0A : (moodColor(mood, now) & 0xFFFFFF);
        int col = 0xFF000000 | base;

        if (frozen) { // текст завис в воздухе
            int lim = phase == 1 ? typed : n;
            for (int i = 0; i < lim; i++) {
                Letter l = letters.get(i);
                if (l.c != ' ') drawLetter(g, mc.font, l.c, l.x, l.y + (float) Math.sin(now / 250.0 + i) * 1.5f, scale, col);
            }
            if (phase == 1) phaseStart = now - (long) typed * GodPayload.MS_PER_CHAR; else phaseStart = now;
            return;
        }

        if (phase == 1) {
            int tc = Math.min(n, (int) (t / GodPayload.MS_PER_CHAR));
            for (int k = typed; k < tc; k++) {
                if (letters.get(k).c == ' ') continue;
                if (satan) play(SND_SATAN, 0.5f + R.nextFloat() * 0.2f, 0.22f);
                else play(SND_TYPE, typePitch(mood), 0.5f);
            }
            typed = tc;
            if (typed >= n) { phase = 2; phaseStart = now; }
        } else if (phase == 2 && t >= GodPayload.HOLD_MS) {
            phase = 3; phaseStart = now; t = 0;
        }

        if (phase == 1 || phase == 2) {
            int lim = phase == 1 ? typed : n;
            for (int i = 0; i < lim; i++) {
                Letter l = letters.get(i);
                if (l.c != ' ') drawLetter(g, mc.font, l.c, l.x + ox, l.y + oy, scale, satan ? 0xCC000000 | base : col);
            }
            return;
        }

        float floorY = H - 30;
        boolean all = true;
        for (int i = 0; i < n; i++) {
            Letter l = letters.get(i);
            if (l.c == ' ' || landed[i]) continue;
            long ft = t - (long) i * GodPayload.FALL_STAGGER_MS;
            if (ft < 0) { drawLetter(g, mc.font, l.c, l.x + ox, l.y + oy, scale, satan ? 0xCC000000 | base : col); all = false; continue; }
            if (satan) {
                if (ft > 1400) { landed[i] = true; continue; }
                int a = (int) (200 * (1f - ft / 1400f));
                drawLetter(g, mc.font, l.c, l.x + ox, l.y + ft * 0.03f, scale, (a << 24) | base);
                all = false;
            } else {
                float y = l.y + 1500f * (ft / 1000f) * (ft / 1000f);
                if (y >= floorY) {
                    landed[i] = true;
                    spawnBoom(l.x + 3 * scale, floorY, mood, now);
                    if ((landedCount++ % 3) == 0) play(SND_BOOM, 1.7f + R.nextFloat() * 0.4f, 0.12f);
                    continue;
                }
                drawLetter(g, mc.font, l.c, l.x + ox, y, scale, col);
                all = false;
            }
        }
        if (all) { phase = 0; cur = null; }
    }
}
