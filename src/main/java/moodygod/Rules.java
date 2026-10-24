package moodygod;

import java.util.*;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;

/** Пул абсурдных правил. Мир по сиду выбирает часть из них. Игроки не знают какие. */
public final class Rules {
    public static final class PState {
        public final Map<String, Integer> m = new HashMap<>();
        public final StringBuilder chat = new StringBuilder();
        public boolean wasGround = true;
        public double lastX, lastY, lastZ, dAcc;
        public int look;
        public long prayTick;
        public void inc(String k) { m.merge(k, 1, Integer::sum); }
        public int n(String k) { return m.getOrDefault(k, 0); }
        public void reset() { m.clear(); chat.setLength(0); dAcc = 0; }
    }

    public record Ctx(ServerPlayer p, ServerLevel lv, PState s, List<ServerPlayer> all) {}
    /** what — «ты <what>» для шаблонов нарушений; bad — своя реплика (может быть null) */
    public record Rule(String id, String reveal, String what, String bad, ToIntFunction<Ctx> eval) {}

    // ---------- хелперы ----------
    static int n(Ctx c, String k) { return c.s().n(k); }
    static boolean held(Ctx c, Item i) { return c.p().getMainHandItem().is(i); }
    static int pm(boolean good, boolean bad) { return good ? 1 : bad ? -1 : 0; }
    static float yd(Ctx c, float target) { return Math.abs(Mth.wrapDegrees(c.p().getYRot() - target)); }
    static boolean said(Ctx c, String w) { return c.s().chat.indexOf(w) >= 0; }
    static int light(Ctx c) { return c.lv().getMaxLocalRawBrightness(c.p().blockPosition()); }
    static boolean slot(Ctx c, EquipmentSlot s) { return c.p().getItemBySlot(s).isEmpty(); }

    public static int countIf(ServerPlayer p, Predicate<ItemStack> f) {
        int c = 0;
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (!st.isEmpty() && f.test(st)) c += st.getCount();
        }
        return c;
    }
    public static int count(ServerPlayer p, Item it) { return countIf(p, s -> s.is(it)); }
    static int emptySlots(ServerPlayer p) {
        int c = 0; var inv = p.getInventory();
        for (int i = 0; i < 36; i++) if (inv.getItem(i).isEmpty()) c++;
        return c;
    }
    public static boolean is(Entity e, String id) {
        return EntityType.getKey(e.getType()).getPath().equals(id);
    }
    static boolean near(Ctx c, String id, double r) {
        return !c.lv().getEntities((Entity) null, c.p().getBoundingBox().inflate(r), e -> is(e, id)).isEmpty();
    }
    static final Set<String> FLOWERS = Set.of("poppy", "dandelion", "blue_orchid", "allium", "azure_bluet",
        "oxeye_daisy", "cornflower", "lily_of_the_valley", "rose_bush", "peony", "lilac", "sunflower");
    static boolean flowerInHand(Ctx c) {
        return FLOWERS.contains(BuiltInRegistries.ITEM.getKey(c.p().getMainHandItem().getItem()).getPath());
    }
    static double friend(Ctx c) {
        double d = 1e9;
        for (ServerPlayer o : c.all()) if (o != c.p()) d = Math.min(d, o.distanceTo(c.p()));
        return d;
    }
    static boolean below(Ctx c, net.minecraft.world.level.block.Block b) {
        return c.lv().getBlockState(c.p().blockPosition().below()).is(b);
    }

    // r — правило с нарушением; rb — со своей репликой; g — только «хорошее» (нарушить нельзя)
    static Rule r(String id, String reveal, String what, ToIntFunction<Ctx> f) { return new Rule(id, reveal, what, null, f); }
    static Rule rb(String id, String reveal, String what, String bad, ToIntFunction<Ctx> f) { return new Rule(id, reveal, what, bad, f); }
    static Rule g(String id, String reveal, ToIntFunction<Ctx> f) { return new Rule(id, reveal, "сделал что-то не так", null, f); }

    public static final List<Rule> ALL = List.of(
        // ===== ВЗГЛЯД / ПОЗА ВО ВРЕМЯ БЕСЕДЫ =====
        rb("east", "Когда я говорю — смотри строго на пчелу, пролетающую на востоке!", "смотрел не на пчелу на востоке",
           "Ты смотрел на ЗАПАД! Пчела была на ВОСТОКЕ! Ты вообще слушаешь?!",
           c -> pm(yd(c, -90) < 30, yd(c, -90) > 150)),
        r("north", "Смотри на север. Там зима близко.", "смотрел на юг, где лето и это неправильно",
           c -> pm(yd(c, 180) < 30, yd(c, 0) < 30)),
        r("sky", "Смотри в небо, а не под ноги, червь!", "пялился себе под ноги, как червь",
           c -> pm(c.p().getXRot() < -50, c.p().getXRot() > 70)),
        r("bow", "Кланяйся, пока я говорю! Приседай! И не вздумай бегать!", "стоял прямо и бегал при Боге",
           c -> pm(c.p().isShiftKeyDown(), c.p().isSprinting())),
        r("nojump", "При мне не прыгать. Земля — моя, воздух — тоже.", "прыгал, пока я говорил",
           c -> c.p().onGround() ? 0 : -1),
        r("swim", "При мне не плавать. Утки обижаются.", "плавал при Боге",
           c -> c.p().isSwimming() ? -1 : 0),
        r("eat", "Пока Бог говорит — не жрать. Это неуважение.", "жрал, пока Бог говорил",
           c -> c.p().isUsingItem() ? -1 : 0),
        r("ride", "На ком-то ездить при мне нельзя. Я не люблю таксистов.", "ехал на ком-то, пока я говорил",
           c -> c.p().isPassenger() ? -1 : 0),
        r("burn", "Гореть при мне запрещено. Гореть — это моя работа.", "горел при Боге",
           c -> c.p().isOnFire() ? -1 : 0),
        r("fly", "Не летай на элитрах. Ты не птица, ты просто человек с курткой.", "летал, как птица",
           c -> c.p().isFallFlying() ? -1 : 0),
        // ===== В РУКЕ =====
        r("stick", "Во время беседы держи в руке палку. Просто палку. Не спрашивай.", "держал в руке меч вместо священной палки",
           c -> held(c, Items.STICK) ? 1 : c.p().getMainHandItem().is(ItemTags.SWORDS) ? -1 : 0),
        g("dandelion", "Одуванчик в руке — знак уважения. Для Нацуки. То есть для меня.", c -> held(c, Items.DANDELION) ? 1 : 0),
        g("flower", "Цветок в руке — знак уважения. Любой. Кроме бумажных.", c -> flowerInHand(c) ? 1 : 0),
        g("bone", "Кость в руке — к удаче. Не спрашивай, к чьей.", c -> held(c, Items.BONE) ? 1 : 0),
        g("apple", "Яблоко в руке. Ньютон тоже начинал с этого.", c -> held(c, Items.APPLE) ? 1 : 0),
        g("compass", "Держи компас. Ориентируйся на мою доброту.", c -> held(c, Items.COMPASS) ? 1 : 0),
        g("clock", "Часы в руке — уважай время. Мы все умрём.", c -> held(c, Items.CLOCK) ? 1 : 0),
        g("rod", "Удочка в руке. Рыбу я люблю. Рыбаков — не очень.", c -> held(c, Items.FISHING_ROD) ? 1 : 0),
        g("cake", "Торт — ложь, но держи в руке. Портал был прав.", c -> held(c, Items.CAKE) ? 1 : 0),
        g("book", "Читай книгу при мне. Притворись, что умный.", c -> held(c, Items.BOOK) ? 1 : 0),
        g("sapling", "Саженец в руке. Дерево тоже хочет быть услышанным.", c -> c.p().getMainHandItem().is(ItemTags.SAPLINGS) ? 1 : 0),
        g("egg", "Яйцо в руке. Яйцо — это начало. Или омлет.", c -> held(c, Items.EGG) ? 1 : 0),
        g("torchhand", "Факел в руке. Освети мой лик.", c -> held(c, Items.TORCH) ? 1 : 0),
        g("fist", "Руки пустые — сердце чистое.", c -> c.p().getMainHandItem().isEmpty() ? 1 : 0),
        r("shears", "Ножницы при мне не держать. Они… ассоциируются.", "держал ножницы при мне, а они ассоциируются с шерстью",
           c -> held(c, Items.SHEARS) ? -1 : 0),
        r("bucket", "Ведро при мне не держать. Ведро — ложь.", "держал ведро при Боге",
           c -> (held(c, Items.BUCKET) || held(c, Items.WATER_BUCKET)) ? -1 : 0),
        r("pickhand", "С киркой наперевес разговаривать со мной нельзя.", "говорил со мной с киркой наперевес",
           c -> c.p().getMainHandItem().is(ItemTags.PICKAXES) ? -1 : 0),
        r("snowball", "Снежок в руке — прицеливание в Бога. Недопустимо.", "целился в Бога снежком",
           c -> held(c, Items.SNOWBALL) ? -1 : 0),
        // ===== ИНВЕНТАРЬ =====
        r("rotten", "Гнилую плоть в инвентаре не держать. Воняет.", "таскал с собой гнилую плоть",
           c -> count(c.p(), Items.ROTTEN_FLESH) > 0 ? -1 : 0),
        g("bread", "Хлеб носи с собой. Хлеб — это жизнь. И немножко религия.", c -> count(c.p(), Items.BREAD) >= 1 ? 1 : 0),
        r("torch7", "Ровно семь факелов в инвентаре. Семь. Не шесть.", "носил неправильное количество факелов (не семь)",
           c -> { int k = count(c.p(), Items.TORCH); return pm(k == 7, k >= 64); }),
        g("totem", "Тотем бессмертия в сумке — признак здравого ума.", c -> count(c.p(), Items.TOTEM_OF_UNDYING) > 0 ? 1 : 0),
        r("pearl", "Жемчуг Края — это побег от ответственности. Не носи.", "таскал жемчуг Края, чтобы сбежать от ответственности",
           c -> count(c.p(), Items.ENDER_PEARL) > 0 ? -1 : 0),
        r("egapple", "Зачарованное золотое яблоко — жадность. Убери.", "носил зачарованное золотое яблоко, жадина",
           c -> count(c.p(), Items.ENCHANTED_GOLDEN_APPLE) > 0 ? -1 : 0),
        r("tnt", "Динамит в кармане — к неприятностям. Не при мне.", "таскал динамит в кармане",
           c -> count(c.p(), Items.TNT) > 0 ? -1 : 0),
        g("dirt64", "Целый стак земли — вот богатство. Хоть и ничтожное.", c -> count(c.p(), Items.DIRT) >= 64 ? 1 : 0),
        r("diamonds", "Пять и больше алмазов — подозрительно богатый ты.", "ходил с алмазами, как олигарх",
           c -> count(c.p(), Items.DIAMOND) >= 5 ? -1 : 0),
        g("cod", "Рыбка в сумке — рыбке радость.", c -> count(c.p(), Items.COD) > 0 ? 1 : 0),
        g("cookie", "Печеньки в сумке. Для Нацуки. То есть для меня. Не для тебя.", c -> count(c.p(), Items.COOKIE) > 0 ? 1 : 0),
        g("pie", "Тыквенный пирог в сумке. Мне нужно. Нет, не отдавай.", c -> count(c.p(), Items.PUMPKIN_PIE) > 0 ? 1 : 0),
        g("milk", "Молоко с собой. Кальций — это вера.", c -> count(c.p(), Items.MILK_BUCKET) > 0 ? 1 : 0),
        r("nametag", "Бирки для имён не таскать. Не называй чужое.", "таскал бирки для имён, как оккупант",
           c -> count(c.p(), Items.NAME_TAG) > 0 ? -1 : 0),
        r("hoarder", "Забитый до отказа инвентарь — жадность. Живи легче.", "забил инвентарь до отказа, барахольщик",
           c -> pm(emptySlots(c.p()) >= 30, emptySlots(c.p()) == 0)),
        // ===== ОДЕЖДА =====
        g("pumpkin", "Голова должна быть в тыкве. Как у Джека.", c -> c.p().getItemBySlot(EquipmentSlot.HEAD).is(Items.CARVED_PUMPKIN) ? 1 : 0),
        r("armor", "Броня — это гордыня. Ходи в майке, как мужик.", "ходил в нетеритовой броне, гордыня",
           c -> slot(c, EquipmentSlot.CHEST) ? 1 : c.p().getItemBySlot(EquipmentSlot.CHEST).is(Items.NETHERITE_CHESTPLATE) ? -1 : 0),
        g("boots", "Ходи босиком. Земля тебя любит.", c -> slot(c, EquipmentSlot.FEET) ? 1 : 0),
        r("fullarmor", "Если надел всю броню — ты гордец. Не надевай всю.", "надел всю броню целиком, гордец",
           c -> (!slot(c, EquipmentSlot.HEAD) && !slot(c, EquipmentSlot.CHEST) && !slot(c, EquipmentSlot.LEGS) && !slot(c, EquipmentSlot.FEET)) ? -1 : 0),
        g("shield", "Щит в левой руке. Бог любит оборонительных.", c -> c.p().getOffhandItem().is(Items.SHIELD) ? 1 : 0),
        // ===== СОСТОЯНИЕ =====
        r("hunger", "Сытость должна быть нечётной. Не спрашивай.", "держал чётную сытость",
           c -> c.p().getFoodData().getFoodLevel() % 2 == 1 ? 1 : 0),
        r("stuffed", "Не обжирайся. Чревоугодие — тоже грех.", "был сыт до отвала, чревоугодие",
           c -> c.p().getFoodData().getFoodLevel() >= 20 ? -1 : 0),
        r("lowhp", "Ходить почти мёртвым — моя привилегия. Не воруй её.", "ходил почти мёртвым, а это моя привилегия",
           c -> c.p().getHealth() <= 4 ? -1 : 0),
        r("perfect", "Совершенство — тоже гордыня. Будь хоть чуть побитым.", "был слишком идеальным, при полных хп и сытости",
           c -> (c.p().getHealth() >= 20 && c.p().getFoodData().getFoodLevel() >= 20) ? -1 : 0),
        r("xp3", "Уровень опыта должен делиться на три. Это математика.", "имел несчастливый уровень опыта",
           c -> { int l = c.p().experienceLevel; return pm(l > 0 && l % 3 == 0, l == 13); }),
        r("xp0", "Без опыта к Богу не приходят. Хотя бы один уровень.", "пришёл к Богу без единого уровня опыта",
           c -> c.p().experienceLevel == 0 ? -1 : 0),
        // ===== МЕСТО =====
        r("eveny", "Стой на чётной высоте. Нечётная высота — от лукавого.", "стоял на нечётной высоте",
           c -> Math.floorMod(c.p().blockPosition().getY(), 2) == 0 ? 1 : -1),
        g("oddx", "Стой на нечётной координате X. Чётные — для слабаков.", c -> Math.floorMod(c.p().blockPosition().getX(), 2) == 1 ? 1 : 0),
        g("y64", "Стой строго на уровне моря: Y равно шестьдесят четыре.", c -> c.p().blockPosition().getY() == 64 ? 1 : 0),
        r("depth", "Не копай глубже нуля и не лезь выше сотни.", "забрался слишком глубоко или слишком высоко",
           c -> { int y = c.p().blockPosition().getY(); return (y < 0 || y > 100) ? -1 : 0; }),
        r("grass", "Стой на траве. Песок — это злая пыль.", "стоял на песке, злой пыли",
           c -> pm(below(c, Blocks.GRASS_BLOCK), below(c, Blocks.SAND))),
        r("gravel", "Гравий — это камень, который сдался. Не стой на сдавшихся.", "стоял на гравии, на сдавшихся камнях",
           c -> below(c, Blocks.GRAVEL) ? -1 : 0),
        r("stone", "Не стой на голом камне. Ты не гора.", "стоял на голом камне, а ты не гора",
           c -> below(c, Blocks.STONE) ? -1 : 0),
        r("water", "Мокрых не люблю. Вылезай из воды.", "стоял в воде, мокрый",
           c -> c.p().isInWater() ? -1 : 0),
        r("dark", "Во тьме стоять — богохульство. Зажги свет.", "стоял во тьме, как нечисть",
           c -> light(c) <= 3 ? -1 : 0),
        g("open", "Стой под открытым небом, чтоб я тебя видел.", c -> c.lv().canSeeSky(c.p().blockPosition()) ? 1 : 0),
        r("rain", "Под дождём не мокни. Это слёзы, и они не твои.", "мок под дождём",
           c -> (c.lv().isRaining() && c.lv().canSeeSky(c.p().blockPosition())) ? -1 : 0),
        rb("nether", "В Незер без моего разрешения не ходить. Это территория Сатаны.", "забрёл в Незер, на территорию Сатаны",
           "Ты в Незере?! Это дом Сатаны! Ты что, к нему в гости?! Предатель!",
           c -> c.lv().dimension() == Level.NETHER ? -1 : 0),
        r("desert", "Пустыню не любить. Песок — везде. Как овцы. Нет, хуже.", "торчал в пустыне, где песок везде",
           c -> c.lv().getBiome(c.p().blockPosition()).is(Biomes.DESERT) ? -1 : 0),
        g("plains", "Равнины — дом Бога. Бывай там почаще.", c -> c.lv().getBiome(c.p().blockPosition()).is(Biomes.PLAINS) ? 1 : 0),
        r("swamp", "Болото — территория Шрека. Не заходи, он выйдет.", "торчал на болоте, а там живёт Шрек",
           c -> c.lv().getBiome(c.p().blockPosition()).is(Biomes.SWAMP) ? -1 : 0),
        // ===== КОМПАНИЯ =====
        r("together", "Держитесь вместе. Дружба — это магия. Шучу, это просто правило.", "ушёл далеко от друга",
           c -> { double d = friend(c); return pm(d < 6, d > 60 && d < 1e8); }),
        r("huddle", "Не жмитесь друг к другу. Это неловко.", "прижимался к другу, это неловко",
           c -> friend(c) < 1.5 ? -1 : 0),
        // ===== МОБЫ РЯДОМ =====
        g("cat", "Рядом должна быть кошка. Я люблю кошек. (Пока.)", c -> near(c, "cat", 8) ? 1 : 0),
        g("wolf", "Рядом волк или собака — друг человека. Бог одобряет.", c -> near(c, "wolf", 8) ? 1 : 0),
        g("bee", "Пчела рядом — знак. Пчёлы всё видят.", c -> near(c, "bee", 12) ? 1 : 0),
        g("cow", "Корова рядом — это как друг, только мычит.", c -> near(c, "cow", 8) ? 1 : 0),
        g("golem", "Железный голем рядом — вот это охрана.", c -> near(c, "iron_golem", 10) ? 1 : 0),
        g("pig", "Свинья рядом — к хорошему аппетиту.", c -> near(c, "pig", 8) ? 1 : 0),
        r("villager", "С жителями не тереться. Торгаши мне не нравятся.", "тёрся возле торгаша-жителя",
           c -> near(c, "villager", 6) ? -1 : 0),
        r("creeper", "Криперов рядом быть не должно. Они мои пережитки.", "стоял рядом с крипером",
           c -> near(c, "creeper", 10) ? -1 : 0),
        r("enderman", "Эндермен рядом — он косо смотрит на меня. Уведи.", "торчал рядом с эндерменом",
           c -> near(c, "enderman", 16) ? -1 : 0),
        r("zombie", "Зомби рядом — значит, ты плохо прибрался.", "не прибрался: рядом стоял зомби",
           c -> near(c, "zombie", 10) ? -1 : 0),
        r("chicken", "Рядом с курицей стоять нельзя. Курица — моя тётя Люся, ей неловко.", "стоял рядом с курицей, а это тётя Люся",
           c -> near(c, "chicken", 6) ? -1 : 0),
        // ===== СЧЁТЧИКИ ЗА ИНТЕРВАЛ =====
        r("jumps", "Прыгай примерно десять раз за мой интервал. Не пять. Не двадцать.", "прыгал неправильное число раз",
           c -> pm(n(c, "jumps") >= 8 && n(c, "jumps") <= 12, n(c, "jumps") == 0 || n(c, "jumps") > 40)),
        r("nosprint", "Не бегай. Хромая походка богоугодна.", "носился как угорелый",
           c -> n(c, "sprintSec") > 25 ? -1 : n(c, "sprintSec") == 0 ? 1 : 0),
        r("walk", "Пройди сто-триста блоков за интервал. Не меньше, не больше.", "лентяй, прошёл меньше тридцати блоков",
           c -> pm(n(c, "dist") >= 100 && n(c, "dist") <= 300, n(c, "dist") < 30)),
        g("sneakdance", "Присядь секунд на десять за интервал. Это танец.", c -> n(c, "sneakSec") >= 10 ? 1 : 0),
        r("splash", "Не плескайся. Это не бассейн, это моя вселенная.", "плескался в воде",
           c -> n(c, "swimSec") > 5 ? -1 : 0),
        r("fire", "Не горели? Горели. Огонь — это не вам.", "горел, как свечка",
           c -> n(c, "fireSec") > 0 ? -1 : 0),
        r("flyer", "Не летать больше трёх секунд. Птицы — мои конкуренты.", "летал дольше трёх секунд",
           c -> n(c, "flySec") > 3 ? -1 : 0),
        r("rider", "Езда верхом — признак лени. Меньше тридцати секунд.", "ехал, а не шёл, больше тридцати секунд",
           c -> n(c, "rideSec") > 30 ? -1 : 0),
        r("wetrain", "Долго мок под дождём. Простудишься — и это моя вина?", "долго мок под дождём",
           c -> n(c, "rainSec") > 30 ? -1 : 0),
        r("dark2", "Минута во тьме — уже грех. Свет не стоит экономить.", "жил впотьмах больше минуты",
           c -> n(c, "darkSec") > 60 ? -1 : 0),
        r("hurt", "Получать по морде — не добродетель. Но и не грех. До восьми раз.", "получил по морде восемь и более раз",
           c -> pm(n(c, "hurt") == 0, n(c, "hurt") >= 8)),
        g("fistfight", "Бей кулаками. Хоть раз за интервал. Честь превыше всего.", c -> n(c, "fist") >= 1 ? 1 : 0),
        r("deaths", "Умирать — моя привилегия. Don't Starve сказал бы: «Ещё раз».", "умер без моего разрешения",
           c -> n(c, "deaths") > 0 ? -1 : 0),
        r("blocks", "Ломай не больше двухсот блоков. Лень — добродетель.", "сломал слишком много блоков",
           c -> pm(n(c, "blocks") < 40, n(c, "blocks") > 200)),
        // ===== УБИЙСТВА =====
        g("zombie_kills", "Убей трёх зомби. Они мусор, а мусор надо выносить.", c -> n(c, "k_zombie") >= 3 ? 1 : 0),
        r("skeleton", "Скелетов не бить. Они родня Санса. А Санс — друг.", "убил скелета, родственника Санса",
           c -> n(c, "k_skeleton") > 0 ? -1 : 0),
        g("spider", "Паукам — смерть. Я арахнофоб. Нет, не шучу.", c -> n(c, "k_spider") > 0 ? 1 : 0),
        g("endermankill", "Убей эндермена. Он слишком высокий и косо смотрит.", c -> n(c, "k_enderman") > 0 ? 1 : 0),
        g("witch", "Убей ведьму. Она варит зелья без лицензии.", c -> n(c, "k_witch") > 0 ? 1 : 0),
        g("phantom", "Убей фантома. Они — следствие недосыпа, а я против сна.", c -> n(c, "k_phantom") > 0 ? 1 : 0),
        g("pigkill", "Свинья — это мой завтрак. Принеси мне бекон.", c -> n(c, "k_pig") > 0 ? 1 : 0),
        r("cowkill", "Коров не убивай. Они знали, что такое «Му».", "убил корову, а она знала что такое «Му»",
           c -> n(c, "k_cow") > 0 ? -1 : 0),
        r("villagerkill", "Жителей не убивать. Это мои прихожане. Исключительно мои.", "убил моего прихожанина-жителя",
           c -> n(c, "k_villager") > 0 ? -1 : 0),
        r("catkill", "Кошек не убивать. Убиваю их только я.", "убил кошку, а убивать их могу только я",
           c -> n(c, "k_cat") > 0 ? -1 : 0),
        r("wolfkill", "Собак не убивать. Верность — это святое.", "убил собаку, друга человека",
           c -> n(c, "k_wolf") > 0 ? -1 : 0),
        r("chickenkill", "Не бей куриц. Курица — моя тётя Люся.", "убил курицу, а это тётя Люся",
           c -> n(c, "k_chicken") > 0 ? -1 : 0),
        g("creeperkill", "Убей хоть одного крипера. Он слишком громкий. Aw man.", c -> n(c, "k_creeper") > 0 ? 1 : 0),
        // ===== ДОБЫЧА / СЛУЧАЙНЫЕ НАРУШЕНИЯ =====
        rb("ironsneak", "Добывая железо — приседай. Железо любит поклоны.", "добывал железо, не присев",
           "Ты добывал железо СТОЯ?! Железо любит поклоны! Я же нигде этого не говорил! Но оно любит!",
           c -> pm(n(c, "ironS") > 0 && n(c, "ironN") == 0, n(c, "ironN") > 0)),
        r("coalsneak", "Уголь добывай, пригнувшись. Он пачкается и стесняется.", "добывал уголь, не присев",
           c -> pm(n(c, "coalS") > 0 && n(c, "coalN") == 0, n(c, "coalN") > 0)),
        rb("diamond", "Алмазная руда принадлежит мне. Тронешь — пожалеешь.", "тронул мою алмазную руду",
           "Это были МОИ алмазы! МОИ! Я их туда положил! Для себя!",
           c -> n(c, "diamond") > 0 ? -1 : 0),
        r("redstone", "Красный камень — мой бывший. Не трогай, он ядовитый.", "трогал красный камень, моего бывшего",
           c -> n(c, "redstone") > 0 ? -1 : 0),
        g("lapis", "Лазурит — любимый камень Бога. Добудь хоть кусочек.", c -> n(c, "lapis") > 0 ? 1 : 0),
        r("emerald", "Изумруды — это взятка. Торговля — грех.", "добыл изумруд, а изумруды — это взятка",
           c -> n(c, "emerald") > 0 ? -1 : 0),
        g("copper", "Медь — зелёный металл будущего. Добудь немного.", c -> n(c, "copper") > 0 ? 1 : 0),
        r("debris", "Древние обломки спят. Не буди их.", "разбудил древние обломки",
           c -> n(c, "debris") > 0 ? -1 : 0),
        g("obsidian", "Обсидиан добывать — добродетель. Он тёмный, как моя душа.", c -> n(c, "obsidian") > 0 ? 1 : 0),
        g("logs", "Руби деревья. Дереву нужна свобода от самого себя.", c -> n(c, "logs") >= 10 ? 1 : 0),
        r("leaves", "Листьям больно, когда их ломают. Хватит.", "ломал листья, а им больно",
           c -> n(c, "leaves") > 15 ? -1 : 0),
        rb("flowers", "Цветы не рвать. Цветок — это Нацуки.", "сломал цветок, то есть Нацуки",
           "Ты сломал цветок?! Это была Нацуки! Ты убил Нацуки! Ааа!",
           c -> n(c, "flower") > 0 ? -1 : 0),
        r("crops", "Урожай не трогай. Хлеб растёт сам по себе, а ты ему мешаешь.", "ломал урожай, а хлеб растёт сам",
           c -> n(c, "crops") > 0 ? -1 : 0),
        r("dirt", "Землю не копай много. Прах есть прах.", "перекопал землю",
           c -> n(c, "dirt") > 30 ? -1 : 0),
        r("sand", "Песка нельзя много. Он везде. Как овцы.", "накопал слишком много песка",
           c -> n(c, "sand") > 20 ? -1 : 0),
        r("gravel2", "Гравия нельзя ломать. Он и так сдался.", "ломал гравий, и так сдавшийся",
           c -> n(c, "gravel") > 5 ? -1 : 0),
        r("cobble", "Сто двадцать камней — предел. Дальше это уже одержимость.", "наколотил больше сотни камней",
           c -> n(c, "stone") > 120 ? -1 : 0),
        r("glass", "Стекло не бить. Я живу в стеклянном доме и бросаюсь камнями.", "бил стекло в доме Бога",
           c -> n(c, "glass") > 0 ? -1 : 0),
        // ===== ЧАТ =====
        g("dumpling", "Скажи в чат слово «пельмень». Громко.", c -> said(c, "пельмен") || said(c, "pelmen") ? 1 : 0),
        r("lag", "Слово «лаг» запрещено. Слышишь? ЗАПРЕЩЕНО.", "сказал запрещённое слово «лаг»",
           c -> said(c, " лаг") || said(c, " lag ") ? -1 : 0),
        g("pickle", "Объяви в чате, что ты Пикл Рик. Я жду.", c -> said(c, "пикл рик") || said(c, "pickle rick") ? 1 : 0),
        g("hello", "Поздоровайся в чате словом «привет». Богу приятно.", c -> said(c, "привет") ? 1 : 0),
        g("please", "Скажи «пожалуйста» в чате. Хоть раз за мой интервал.", c -> said(c, "пожалуйста") ? 1 : 0),
        r("caps", "Не ори капсом. Орать буду я.", "орал капсом, а орать буду я",
           c -> n(c, "caps") > 0 ? -1 : 0),
        r("talk", "Молчание — золото. Шесть сообщений — это уже болтовня.", "болтал в чате, как не в себя",
           c -> pm(n(c, "msgs") == 0, n(c, "msgs") >= 6)),
        r("lol", "Слово «лол» запрещено. Это не смех, а диагноз.", "сказал «лол», а это диагноз",
           c -> said(c, "лол") ? -1 : 0),
        r("godword", "Не поминай Бога в чате всуе. Бог — слово не для чата.", "упомянул Бога в чате всуе",
           c -> said(c, " бог") ? -1 : 0),
        rb("sheepword", "Слово на букву «О» с корнем «овц» не произносить вообще.", "произнёс слово на букву «О», которое нельзя",
           "Ты сказал это СЛОВО! Слово на букву О! Не при мне! НЕ ПРИ МНЕ!",
           c -> said(c, "овц") && !said(c, "ненавижу овец") ? -1 : 0),
        g("hate", "Напиши в чате «ненавижу овец». Бог любит единомышленников.", c -> said(c, "ненавижу овец") ? 1 : 0)
    );
}
