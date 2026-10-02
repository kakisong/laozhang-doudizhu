package com.kaynzhang.doudizhu.data

/** Device-independent artwork configuration; IDs are permanent save-file values. */
enum class PortraitFace { OVAL, ROUND, SQUARE, LONG, HEART, ANGULAR, BROAD }
enum class PortraitHair { SIDE_PART, CREW, WAVES, BOB, BUN, PONYTAIL, BRAID, CURLS, RECEDING, BALD, POMPADOUR, FRINGE }
enum class PortraitHat { NONE, FLAT_CAP, WORK_CAP, BASEBALL, BERET, BOWLER }
enum class PortraitGlasses { NONE, ROUND, RECTANGLE, HALF_FRAME }
enum class PortraitBeard { NONE, MOUSTACHE, GOATEE, FULL, STUBBLE }
enum class PortraitOutfit { VEST, LAPELS, CARDIGAN, SCARF, HOODIE, OVERALLS, BOW_TIE, APRON, SHIRT, POLO, BLOUSE }

data class PortraitSpec(
    val id: String,
    val name: String,
    val face: PortraitFace,
    val hair: PortraitHair,
    val hairColor: Long = 0xFF343A32L,
    val skin: Long = 0xFFE4BF9CL,
    val backdrop: Long = 0xFF788C7EL,
    val coat: Long = 0xFF38574BL,
    val accent: Long = 0xFFD4BD8EL,
    val outfit: PortraitOutfit = PortraitOutfit.SHIRT,
    val hat: PortraitHat = PortraitHat.NONE,
    val glasses: PortraitGlasses = PortraitGlasses.NONE,
    val beard: PortraitBeard = PortraitBeard.NONE,
    val senior: Boolean = false,
    val earrings: Boolean = false,
)

object BuiltInPortraits {
    const val DEFAULT_ID = "lao_zhang"

    /** Each person has an authored face, silhouette, clothing and accessory combination. */
    val all: List<PortraitSpec> = listOf(
        PortraitSpec("lao_zhang", "老张", PortraitFace.SQUARE, PortraitHair.SIDE_PART,
            hairColor = 0xFFBBC0ADL, backdrop = 0xFF728677L, coat = 0xFF2C4C43L,
            outfit = PortraitOutfit.VEST, glasses = PortraitGlasses.RECTANGLE, senior = true),
        PortraitSpec("wang_daye", "王大爷", PortraitFace.ROUND, PortraitHair.BALD,
            hairColor = 0xFFD1CFC0L, backdrop = 0xFF948B73L, coat = 0xFF655D45L,
            outfit = PortraitOutfit.CARDIGAN, hat = PortraitHat.FLAT_CAP, senior = true),
        PortraitSpec("li_ayi", "李阿姨", PortraitFace.OVAL, PortraitHair.BOB,
            hairColor = 0xFFACAFA3L, backdrop = 0xFF968F84L, coat = 0xFF6C4B51L, accent = 0xFFCEA790L,
            outfit = PortraitOutfit.SCARF, glasses = PortraitGlasses.ROUND, senior = true, earrings = true),
        PortraitSpec("xiao_zhang", "小张", PortraitFace.LONG, PortraitHair.CREW,
            backdrop = 0xFF6D8991L, coat = 0xFF3E6070L, accent = 0xFFB7CBC9L, outfit = PortraitOutfit.HOODIE),
        PortraitSpec("lao_liu", "老刘", PortraitFace.BROAD, PortraitHair.RECEDING,
            hairColor = 0xFFA9AEA1L, skin = 0xFFD5AC85L, backdrop = 0xFF81887AL, coat = 0xFF645147L,
            outfit = PortraitOutfit.CARDIGAN, beard = PortraitBeard.MOUSTACHE, senior = true),
        PortraitSpec("a_hua", "阿花", PortraitFace.HEART, PortraitHair.BOB,
            hairColor = 0xFF5A4035L, skin = 0xFFF0CEB1L, backdrop = 0xFF948176L, coat = 0xFF81545AL,
            accent = 0xFFE0BD9CL, outfit = PortraitOutfit.BLOUSE, earrings = true),
        PortraitSpec("zhao_laoban", "赵老板", PortraitFace.SQUARE, PortraitHair.POMPADOUR,
            hairColor = 0xFF383B34L, backdrop = 0xFF77786BL, coat = 0xFF354941L,
            outfit = PortraitOutfit.LAPELS, beard = PortraitBeard.MOUSTACHE),
        PortraitSpec("sun_shifu", "孙师傅", PortraitFace.BROAD, PortraitHair.CREW,
            skin = 0xFFD4A981L, backdrop = 0xFF82918BL, coat = 0xFF446678L, accent = 0xFFADC0C2L,
            outfit = PortraitOutfit.OVERALLS, hat = PortraitHat.WORK_CAP),
        PortraitSpec("zhou_tongxue", "周同学", PortraitFace.LONG, PortraitHair.CURLS,
            skin = 0xFFEEC6A5L, backdrop = 0xFF7F9093L, coat = 0xFF536477L,
            outfit = PortraitOutfit.SHIRT, glasses = PortraitGlasses.ROUND),
        PortraitSpec("wu_jie", "吴姐", PortraitFace.ANGULAR, PortraitHair.PONYTAIL,
            hairColor = 0xFF373A32L, backdrop = 0xFF86917AL, coat = 0xFF46634EL, accent = 0xFFD5C392L,
            outfit = PortraitOutfit.APRON, earrings = true),
        PortraitSpec("zheng_ge", "郑哥", PortraitFace.SQUARE, PortraitHair.CREW,
            skin = 0xFFDAB18BL, backdrop = 0xFF6E8585L, coat = 0xFF364E59L,
            outfit = PortraitOutfit.HOODIE, beard = PortraitBeard.STUBBLE),
        PortraitSpec("chen_boshi", "陈博士", PortraitFace.LONG, PortraitHair.SIDE_PART,
            hairColor = 0xFF474338L, backdrop = 0xFF8C8978L, coat = 0xFF4A5349L, accent = 0xFF8C4C4CL,
            outfit = PortraitOutfit.BOW_TIE, glasses = PortraitGlasses.RECTANGLE),
        PortraitSpec("song_nainai", "宋奶奶", PortraitFace.ROUND, PortraitHair.BUN,
            hairColor = 0xFFD0CFC0L, skin = 0xFFEAC7A4L, backdrop = 0xFF918B80L, coat = 0xFF745967L,
            outfit = PortraitOutfit.CARDIGAN, glasses = PortraitGlasses.HALF_FRAME, senior = true),
        PortraitSpec("lin_guniang", "林姑娘", PortraitFace.HEART, PortraitHair.BRAID,
            hairColor = 0xFF4C3E33L, skin = 0xFFF0CEB3L, backdrop = 0xFF869E8BL, coat = 0xFF5A7865L,
            accent = 0xFFD9B289L, outfit = PortraitOutfit.SCARF, earrings = true),
        PortraitSpec("xu_shushu", "徐叔叔", PortraitFace.OVAL, PortraitHair.WAVES,
            hairColor = 0xFF635447L, skin = 0xFFDBB38DL, backdrop = 0xFF7F9083L, coat = 0xFF6E7050L,
            outfit = PortraitOutfit.POLO, beard = PortraitBeard.MOUSTACHE),
        PortraitSpec("tang_jie", "唐姐", PortraitFace.ROUND, PortraitHair.CURLS,
            hairColor = 0xFF514035L, backdrop = 0xFF988579L, coat = 0xFF75484AL, accent = 0xFFDCC394L,
            outfit = PortraitOutfit.SCARF, earrings = true),
        PortraitSpec("he_dashu", "何大叔", PortraitFace.ANGULAR, PortraitHair.BALD,
            hairColor = 0xFF7C8175L, skin = 0xFFCBA37BL, backdrop = 0xFF808B7BL, coat = 0xFF4C5C42L,
            outfit = PortraitOutfit.VEST, beard = PortraitBeard.FULL, senior = true),
        PortraitSpec("lu_xiaodi", "陆小弟", PortraitFace.HEART, PortraitHair.FRINGE,
            skin = 0xFFECC4A5L, backdrop = 0xFF8096A0L, coat = 0xFF426879L, accent = 0xFFCAC8A8L,
            outfit = PortraitOutfit.HOODIE, hat = PortraitHat.BASEBALL),
        PortraitSpec("qian_xiaomei", "钱小妹", PortraitFace.OVAL, PortraitHair.PONYTAIL,
            hairColor = 0xFF5A4336L, skin = 0xFFF0CFB2L, backdrop = 0xFFA39180L, coat = 0xFF86676DL,
            accent = 0xFFDBBA92L, outfit = PortraitOutfit.BLOUSE, earrings = true),
        PortraitSpec("shen_laoshi", "沈老师", PortraitFace.LONG, PortraitHair.SIDE_PART,
            hairColor = 0xFF9EAAA0L, backdrop = 0xFF7D9090L, coat = 0xFF4A5D61L,
            outfit = PortraitOutfit.LAPELS, glasses = PortraitGlasses.HALF_FRAME, senior = true),
        PortraitSpec("qian_duoduo", "钱多多", PortraitFace.ROUND, PortraitHair.POMPADOUR,
            hairColor = 0xFF453A32L, backdrop = 0xFF9B9075L, coat = 0xFF65553FL, accent = 0xFFE1C789L,
            outfit = PortraitOutfit.BOW_TIE, hat = PortraitHat.BOWLER, beard = PortraitBeard.GOATEE),
        PortraitSpec("fang_ayi", "方阿姨", PortraitFace.BROAD, PortraitHair.BUN,
            hairColor = 0xFF5B4D41L, backdrop = 0xFF879482L, coat = 0xFF5A7160L,
            outfit = PortraitOutfit.BLOUSE, hat = PortraitHat.BERET, earrings = true),
        PortraitSpec("deng_ge", "邓哥", PortraitFace.ROUND, PortraitHair.WAVES,
            hairColor = 0xFF3C4036L, skin = 0xFFD8AD85L, backdrop = 0xFF8C8282L, coat = 0xFF724E52L,
            outfit = PortraitOutfit.LAPELS, beard = PortraitBeard.GOATEE),
        PortraitSpec("su_xuejie", "苏学姐", PortraitFace.ANGULAR, PortraitHair.BOB,
            hairColor = 0xFF343C35L, skin = 0xFFE8C5A6L, backdrop = 0xFF7F9490L, coat = 0xFF3B615DL,
            accent = 0xFFB87F74L, outfit = PortraitOutfit.BOW_TIE, glasses = PortraitGlasses.ROUND, earrings = true),
    )

    private val byId = all.associateBy { it.id }
    private val byName = all.associateBy { it.name }
    private val legacyAvatarIds = mapOf(
        "🙂" to DEFAULT_ID, "👴" to "wang_daye", "👵" to "li_ayi", "🧑" to "xiao_zhang",
        "🧔" to "lao_liu", "👩" to "a_hua", "🤵" to "zhao_laoban", "👨‍🔧" to "sun_shifu",
        "🧑‍🎓" to "zhou_tongxue", "👩‍💼" to "wu_jie", "😎" to "zheng_ge",
        "🤑" to "qian_duoduo", "🧑‍🔬" to "chen_boshi",
    )

    fun resolve(idOrName: String): PortraitSpec {
        val key = idOrName.trim()
        byId[key]?.let { return it }
        byName[key]?.let { return it }
        if (key.isEmpty() || key == "我") return byId.getValue(DEFAULT_ID)
        legacyAvatarIds[key.replace("\uFE0F", "")]?.let { return byId.getValue(it) }
        val index = ((key.hashCode().toLong() and 0x7FFFFFFFL) % all.size).toInt()
        return all[index]
    }
}
