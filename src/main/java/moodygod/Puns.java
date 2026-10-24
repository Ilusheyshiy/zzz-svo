package moodygod;

import java.util.*;

/** Наказания (органы, конечности, части мозга, уникальные) и дары. {T} = секунды. */
final class Puns {
    record Pun(String name, int tier, String say, String fx, String special, String[] undo, String[] cmds) {}
    static final String[] NO = {};
    static Pun p(String n, int t, String say, String fx, String sp, String[] undo, String... cmds) {
        return new Pun(n, t, say, fx, sp, undo, cmds);
    }
    static final String SL = "run effect give @s minecraft:slowness {T} ";
    static final String JB = "run effect give @s minecraft:jump_boost {T} 200 true";
    static final String MF = "run effect give @s minecraft:mining_fatigue {T} ";
    static final String WK = "run effect give @s minecraft:weakness {T} ";

    static final List<Pun> ALL = List.of(
        // --- тяжесть 1 ---
        p("Правая нога отключена", 1, "Правую ногу я забираю на {T} секунд. Прыгать нечем.", null, null, NO, SL + "1 true", JB),
        p("Левая рука отключена", 1, "Левая рука отключена на {T} секунд. Держи правой.", null, null, NO, MF + "2 true", WK + "1 true"),
        p("Левый глаз выколот", 1, "Левый глаз выколот на {T} секунд. Красота.", "eye:L", null, NO),
        p("Правый глаз выколот", 1, "Правый глаз выколот на {T} секунд. Смотри левым.", "eye:R", null, NO),
        p("Обоняние удалено", 1, "Нос удалён на {T} секунд. Овец ты теперь не чуешь, но и они тебя.", null, null, NO, "run effect give @s minecraft:nausea {T} 0 true"),
        p("Мозжечок удалён", 1, "Мозжечок удалён на {T} секунд. Ходи как пьяный.", null, null, NO, "run effect give @s minecraft:nausea {T} 0 true", SL + "0 true"),
        p("Слух отключён", 1, "Слух отключён на {T} секунд. Теперь тебя никто не слышит.", "deaf", null, NO),
        p("Желудок удалён", 1, "Желудок я заберу на {T} секунд. Голод — лучший учитель.", null, null, NO, "run effect give @s minecraft:hunger {T} 4 true"),
        p("Хлебный налог", 1, "Налог на хлеб. Три булки в доход Бога.", null, null, NO, "run clear @s minecraft:bread 3"),
        p("Фантомные боли", 1, "Фантомные боли. Это не больно. Это я издеваюсь.", null, null, NO, "run damage @s 4 minecraft:magic", "run effect give @s minecraft:blindness 5 0 true"),
        // --- тяжесть 2 ---
        p("Обе ноги отключены", 2, "Обе ноги отключены на {T} секунд. Ползи.", null, null, NO, SL + "4 true", JB),
        p("Обе руки отключены", 2, "Обе руки отключены на {T} секунд. Держи зубами.", null, null, NO, MF + "4 true", WK + "3 true"),
        p("Оба глаза выколоты", 2, "Оба глаза выколоты на {T} секунд. Сюжет «Дарена».", "eye:B", null, NO, "run effect give @s minecraft:blindness {T} 0 true"),
        p("Язык отрезан", 2, "Язык отрезан. Ты немой на {T} секунд.", null, "mute", NO),
        p("Лобная доля удалена", 2, "Лобная доля удалена. Самоконтроль на нуле, подёргивает {T} секунд.", null, "twitch", NO),
        p("Сердце уменьшено", 2, "У тебя теперь пять сердец на {T} секунд. Береги.", null, null,
            new String[]{"run attribute @s minecraft:max_health base set 20"}, "run attribute @s minecraft:max_health base set 10"),
        p("Зрительная кора удалена", 2, "Зрительная кора выключена на {T} секунд. Свет — это иллюзия.", null, null, NO, "run effect give @s minecraft:darkness {T} 0 true", "run effect give @s minecraft:blindness 6 0 true"),
        p("Слух и обоняние удалены", 2, "Слух и нос выключены на {T} секунд. Ты как камень.", "deaf", null, NO, "run effect give @s minecraft:nausea {T} 0 true"),
        p("Молния в зад", 2, "Огненный привет с небес. Мои лучшие молнии для тебя.", null, null, NO, "run summon minecraft:lightning_bolt ~ ~ ~", "run damage @s 6 minecraft:magic"),
        p("Икар", 2, "Лети, Икар. Лети и падай.", null, null, NO, "run tp @s ~ ~40 ~", "run effect give @s minecraft:slow_falling 25 0 true"),
        p("Лёгкие сдуты", 2, "Лёгкие сдуты на {T} секунд. Дыши глубже.", null, null, NO, "run effect give @s minecraft:hunger {T} 3 true", SL + "2 true"),
        // --- тяжесть 3 ---
        p("Позвоночник удалён", 3, "Позвоночник изъят на {T} секунд. Ты тряпка.", null, null, NO, SL + "6 true", WK + "4 true", JB),
        p("Все четыре конечности", 3, "Руки и ноги отключены на {T} секунд. Ты бревно.", null, null, NO, SL + "4 true", JB, MF + "4 true", WK + "3 true"),
        p("Память стёрта", 3, "Пять уровней опыта стёрты. Что мы обсуждали? Я не помню.", null, null, NO, "run experience add @s -5 levels", "run effect give @s minecraft:darkness 10 0 true"),
        p("Мозг почти удалён", 3, "Мозг почти полностью удалён на {T} секунд. Зато счастлив.", "eye:B", "twitch,mute", NO, "run effect give @s minecraft:nausea {T} 0 true"),
        p("Сердце на ниточке", 3, "Сердце уменьшено до трёх на {T} секунд. Не шали.", null, null,
            new String[]{"run attribute @s minecraft:max_health base set 20"}, "run attribute @s minecraft:max_health base set 6"),
        p("Вечная ночь", 3, "Вечная ночь на {T} секунд. Привет, Don't Starve.", null, null, NO, "run effect give @s minecraft:darkness {T} 0 true", "run weather thunder")
    );

    static final Pun NOPRAY = p("Безбожие", 2, "Ты не помолился. Безбожник. Левая рука и правая нога отключены на {T} секунд.", null, null, NO,
        MF + "3 true", WK + "2 true", SL + "2 true", JB);
    static final Pun SHEEP = p("Овечье проклятие", 3, "ОВЕЧЬЕ ПРОКЛЯТИЕ! ПОЛУЧАЙ СТАДО!", null, null, NO,
        "run summon minecraft:sheep ~3 ~ ~ {CustomName:\"Блеее\"}", "run summon minecraft:sheep ~-3 ~ ~ {CustomName:\"Блеее\"}",
        "run summon minecraft:sheep ~ ~ ~3 {CustomName:\"Блеее\"}", "run summon minecraft:sheep ~ ~ ~-3 {CustomName:\"Блеее\"}",
        "run summon minecraft:sheep ~2 ~ ~2 {CustomName:\"Блеее\"}", "run summon minecraft:sheep ~-2 ~ ~-2 {CustomName:\"Блеее\"}",
        "run playsound minecraft:entity.sheep.ambient master @s ~ ~ ~ 3 0.5", "run effect give @s minecraft:nausea 10 0 true");
    static final Pun GIFT_BURN = p("Сожжённый дар", 3, "Ты сжёг мой дар. Жопа тебе. Полная. Тотальная.", null, "twitch", NO,
        "run summon minecraft:lightning_bolt ~ ~ ~", "run damage @s 8 minecraft:magic", SL + "4 true", JB);

    static final Map<String, Pun> UNIQUE = Map.ofEntries(
        Map.entry("ironsneak", p("Железная хватка", 2, "Железо любит поклоны, а ты стоял! Обе руки отключены на {T} секунд.", null, null, NO, MF + "5 true", WK + "3 true")),
        Map.entry("coalsneak", p("Закопчённые лёгкие", 2, "Уголь пачкает! Глаза закопчены на {T} секунд.", null, null, NO, "run effect give @s minecraft:blindness 8 0 true", "run effect give @s minecraft:hunger {T} 2 true")),
        Map.entry("flowers", p("Нацуки плачет", 2, "Нацуки плачет. И ты поплачешь: тошнит на {T} секунд.", null, null, NO, "run effect give @s minecraft:nausea {T} 0 true")),
        Map.entry("chickenkill", p("Тётя Люся", 3, "ТЁТЯ ЛЮСЯ! ОТОМСТИ ЗА СЕБЯ! Куры, на него!", null, null, NO,
            "run summon minecraft:chicken ~ ~6 ~ {CustomName:\"Тётя Люся\"}", "run summon minecraft:chicken ~1 ~7 ~ {CustomName:\"Тётя Люся\"}",
            "run summon minecraft:chicken ~-1 ~8 ~ {CustomName:\"Тётя Люся\"}", "run summon minecraft:chicken ~ ~9 ~1 {CustomName:\"Тётя Люся\"}",
            "run summon minecraft:chicken ~ ~10 ~-1 {CustomName:\"Тётя Люся\"}")),
        Map.entry("catkill", p("Кошачья карма", 2, "Кошки всё видят. Левитация на {T} секунд.", null, null, NO, "run effect give @s minecraft:levitation 5 1 true", SL + "1 true")),
        Map.entry("villagerkill", p("Месть прихожан", 3, "Жителя убил? Руки удалены на {T} секунд.", null, null, NO, MF + "5 true", WK + "4 true")),
        Map.entry("caps", p("Речевой центр удалён", 2, "Орать буду я. Речевой центр удалён на {T} секунд.", null, "mute", NO)),
        Map.entry("lag", p("Язык вырезан", 2, "Слово «лаг» произнесено. Язык вырезан на {T} секунд.", null, "mute", NO)),
        Map.entry("sheepword", p("Табуированное слово", 3, "Это слово нельзя было! Язык вырезан на {T} секунд.", null, "mute", NO)),
        Map.entry("diamond", p("Алмазы мои", 2, "Алмазы мои. Забираю все. Все! До единого.", null, null, NO, "run clear @s minecraft:diamond")),
        Map.entry("nether", p("Метка Сатаны", 2, "Метка Сатаны. Горишь, предатель.", null, null, NO, "run damage @s 4 minecraft:on_fire", "run damage @s 3 minecraft:magic")),
        Map.entry("east", p("Куда смотришь", 1, "Куда ты смотрел? Лобная доля качается {T} секунд.", null, "twitch", NO)),
        Map.entry("caps2", NOPRAY));

    static Pun random(int tier, Random r) {
        List<Pun> l = new ArrayList<>();
        for (Pun x : ALL) if (x.tier() == tier) l.add(x);
        return l.get(r.nextInt(l.size()));
    }

    static final String[][] GIFTS = {
        {"Кекс Нацуки (н-не для тебя!)", "run give @s minecraft:cookie[custom_name=\"Дар: Кекс Нацуки\"] 4"},
        {"Сычуаньский соус", "run give @s minecraft:honey_bottle[custom_name=\"Дар: Сычуаньский соус\"] 1"},
        {"Пикл Рик (НЕ СТАВЬ!)", "run give @s minecraft:sea_pickle[custom_name=\"Дар: Пикл Рик\"] 1"},
        {"Священная палка", "run give @s minecraft:stick[custom_name=\"Дар: Священная палка\"] 1"},
        {"Месть овцам", "run give @s minecraft:cooked_mutton[custom_name=\"Дар: Месть овцам\"] 4"},
        {"Хлеб-спаситель", "run give @s minecraft:bread[custom_name=\"Дар: Хлеб-спаситель\"] 5"},
        {"Золотая морковка", "run give @s minecraft:golden_carrot[custom_name=\"Дар: Морковка Баггса\"] 3"},
        {"Изумруды на пиво", "run give @s minecraft:emerald[custom_name=\"Дар: На пиво\"] 2"},
        {"3 уровня опыта", "run experience add @s 3 levels"},
        {"Регенерация", "run effect give @s minecraft:regeneration 20 1"},
        {"Скорость Соника", "run effect give @s minecraft:speed 60 2"},
        {"Ночное зрение", "run effect give @s minecraft:night_vision 120 0"},
        {"Невидимость Фродо", "run effect give @s minecraft:invisibility 60 0"},
        {"Прыжок Марио", "run effect give @s minecraft:jump_boost 40 3"},
        {"Щит от Сатаны", "run give @s minecraft:shield[custom_name=\"Дар: Щит от Сатаны\"] 1"},
        {"Тот самый торт (ложь)", "run give @s minecraft:cake[custom_name=\"Дар: Торт (не ложь)\"] 1"},
        {"Золотой билет", "run give @s minecraft:paper[custom_name=\"Дар: Золотой билет\"] 1"},
        {"Подписка Netflix (без рекламы)", "run give @s minecraft:paper[custom_name=\"Дар: Подписка Netflix\"] 1"},
        {"Банка огурцов", "run give @s minecraft:glass_bottle[custom_name=\"Дар: Банка огурцов\"] 1"},
        {"Ананас (не в жопу!)", "run give @s minecraft:melon_slice[custom_name=\"Дар: Ананас\"] 3"},
        {"Сыр", "run give @s minecraft:pumpkin_pie[custom_name=\"Дар: Сыр\"] 2"},
        {"Ведро воды (без овец)", "run give @s minecraft:water_bucket[custom_name=\"Дар: Вода без овец\"] 1"},
        {"Зелёный человечек", "run give @s minecraft:slime_ball[custom_name=\"Дар: Зелёный человечек\"] 1"},
        {"Меч Мастера", "run give @s minecraft:iron_sword[custom_name=\"Дар: Меч Мастера\"] 1"},
        {"Кирка Стива", "run give @s minecraft:iron_pickaxe[custom_name=\"Дар: Кирка Стива\"] 1"},
        {"Золотые шестерёнки", "run give @s minecraft:gold_ingot[custom_name=\"Дар: Шестерёнки\"] 2"},
        {"Алмаз (редкий)", "run give @s minecraft:diamond[custom_name=\"Дар: Редкий алмаз\"] 1"},
        {"Жемчуг Края", "run give @s minecraft:ender_pearl[custom_name=\"Дар: Жемчуг\"] 2"},
        {"Тотем бессмертия", "run give @s minecraft:totem_of_undying[custom_name=\"Дар: Страховка\"] 1"},
        {"Рыба мема", "run give @s minecraft:cooked_cod[custom_name=\"Дар: Рыба мема\"] 5"},
        {"Хрустящий нагетс", "run give @s minecraft:cooked_chicken[custom_name=\"Дар: Нагетс\"] 3"},
        {"Бекон", "run give @s minecraft:cooked_porkchop[custom_name=\"Дар: Бекон\"] 4"}
    };
}
