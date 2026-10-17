package com.crazystandby

/** Hand-made artwork data: constellations, wolf silhouette, pixel cat, bowl, bolt, pixel digits. */
internal object Art {
    /** stars = x,y pairs in 0..1 (box is 1.4 : 1), edges = index pairs */
    class Con(val name: String, val stars: FloatArray, val edges: IntArray)

    val BEAR = Con("Ursa Major",
        floatArrayOf(0.04f, 0.20f, 0.15f, 0.30f, 0.27f, 0.34f, 0.40f, 0.38f, 0.42f, 0.62f, 0.66f, 0.66f, 0.68f, 0.40f, 0.78f, 0.30f, 0.84f, 0.17f, 0.96f, 0.38f, 0.70f, 0.86f, 0.80f, 0.93f, 0.38f, 0.84f, 0.30f, 0.93f),
        intArrayOf(0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 3, 6, 7, 7, 8, 7, 9, 8, 9, 5, 10, 10, 11, 4, 12, 12, 13))

    val LION = Con("Leo",
        floatArrayOf(0.20f, 0.80f, 0.16f, 0.62f, 0.20f, 0.44f, 0.30f, 0.30f, 0.42f, 0.22f, 0.36f, 0.10f, 0.62f, 0.34f, 0.64f, 0.60f, 0.92f, 0.46f, 0.97f, 0.30f, 0.58f, 0.82f, 0.50f, 0.93f, 0.24f, 0.94f),
        intArrayOf(0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 2, 6, 6, 7, 7, 0, 6, 8, 7, 8, 8, 9, 7, 10, 10, 11, 0, 12))

    val HORSE = Con("Pegasus",
        floatArrayOf(0.42f, 0.30f, 0.44f, 0.62f, 0.72f, 0.64f, 0.70f, 0.30f, 0.30f, 0.18f, 0.19f, 0.10f, 0.05f, 0.24f, 0.14f, 0.34f, 0.36f, 0.80f, 0.28f, 0.92f, 0.76f, 0.82f, 0.84f, 0.93f, 0.88f, 0.22f, 0.97f, 0.34f),
        intArrayOf(0, 1, 1, 2, 2, 3, 3, 0, 0, 4, 4, 5, 5, 6, 6, 7, 7, 4, 1, 8, 8, 9, 2, 10, 10, 11, 3, 12, 12, 13))

    /** Wolf silhouette outline, x,y pairs in a 0..100 box, y grows downward, base at y = 100 */
    val WOLF = floatArrayOf(0f, 100f, 2f, 95f, 8f, 92f, 14f, 91f, 18f, 84f, 20f, 70f, 22f, 62f, 27f, 54f, 31f, 46f, 33f, 38f, 35f, 30f, 37f, 22f, 37f, 15f, 39f, 9f, 42f, 1f, 46f, 8f, 50f, 10f, 57f, 5f, 66f, 0f, 69f, 4f, 63f, 11f, 57f, 16f, 55f, 22f, 58f, 30f, 58f, 40f, 62f, 50f, 64f, 60f, 62f, 72f, 62f, 100f, 70f, 100f)

    val CAT = arrayOf(
        "..XX........XX..",
        ".XXXX......XXXX.",
        ".XPXXXXXXXXXXPX.",
        "XXXXXXXXXXXXXXXX",
        "XXXWEXXXXXXWEXXX",
        "XXXEEXXXXXXEEXXX",
        "XXXXXXXPPXXXXXXX",
        "XXXXXXxXXxXXXXXX",
        ".XXXXXXxxXXXXXX.",
        "...XXXXXXXXXX..X",
        "..XXXXwwwwXXXX.X",
        "..XXXXwwwwXXXXXX",
        "..XXXXwwwwXXXXX.",
        "..XXXXXXXXXXXX..",
        "..XXXX....XXXX.."
    )

    val BOWL = arrayOf(
        "XXXXXXXX",
        "X......X",
        "X......X",
        "X......X",
        ".XXXXXX.",
        "..XXXX.."
    )

    val BOLT = arrayOf(
        "..XX",
        ".XX.",
        "XXXX",
        "..XX",
        ".XX.",
        ".X.."
    )

    /** 5 x 7 pixel digits */
    val DIGITS = arrayOf(
        arrayOf("01110", "10001", "10011", "10101", "11001", "10001", "01110"),
        arrayOf("00100", "01100", "00100", "00100", "00100", "00100", "01110"),
        arrayOf("01110", "10001", "00001", "00010", "00100", "01000", "11111"),
        arrayOf("11110", "00001", "00001", "01110", "00001", "00001", "11110"),
        arrayOf("00010", "00110", "01010", "10010", "11111", "00010", "00010"),
        arrayOf("11111", "10000", "11110", "00001", "00001", "10001", "01110"),
        arrayOf("00110", "01000", "10000", "11110", "10001", "10001", "01110"),
        arrayOf("11111", "00001", "00010", "00100", "01000", "01000", "01000"),
        arrayOf("01110", "10001", "10001", "01110", "10001", "10001", "01110"),
        arrayOf("01110", "10001", "10001", "01111", "00001", "00010", "01100")
    )
}
