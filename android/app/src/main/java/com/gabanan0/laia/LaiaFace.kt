package com.gabanan0.laia

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Base64
import java.io.ByteArrayOutputStream

object LaiaFace {
    private val palette = intArrayOf(
        Color.rgb(0xF3,0xCF,0xB1), Color.rgb(0xD9,0x9B,0x6F), Color.rgb(0xA9,0x5A,0x35),
        Color.rgb(0x61,0x30,0x20), Color.rgb(0x39,0x1B,0x14), Color.rgb(0x26,0x17,0x14),
        Color.rgb(0x1F,0x15,0x14), Color.rgb(0x18,0x12,0x11), Color.rgb(0x18,0x0F,0x0E),
        Color.rgb(0x15,0x0E,0x0D), Color.rgb(0x13,0x0D,0x0C), Color.rgb(0x10,0x0A,0x0A),
        Color.rgb(0x08,0x06,0x06), Color.rgb(0x05,0x04,0x04), Color.rgb(0x00,0x00,0x00)
    )

    private val rows = arrayOf(
        "................................",
        "...........DCDDDDDDDD...........",
        "........DDDDDDDDDDDCCCCD........",
        ".......DDDDB966666ACDDCDD.......",
        "......DDD9567BBABA675ADDDD......",
        ".....DD956AA999A7669A66CDDD.....",
        "....CD66BBBBABAA94358A75ADCC....",
        "...BD66AAAA98988432348A968C44...",
        "..CD67AA86AAAA9532112469B5844C..",
        "..CA6A9954A988532101134A995BCD..",
        "..C5AAA9A8834532100001269976DC..",
        ".D77AAB9A532231000111224BAB6CCD.",
        ".D5A9995321110012356783389A77DC.",
        ".97BAA9855532002553222358AAB6DC.",
        ".77BA8432333200342488433584A6CD.",
        ".5AA834A9A522013233458A348386AD.",
        ".59998B23532101121242454698A6AD.",
        ".6A378422211002110123332489A79D.",
        ".59995221100002210011112488968D.",
        ".5B873100000001220000012466868C.",
        ".7998310000011233100002354246BC.",
        "..696520000012432100002453145C..",
        "..597742000000111111123886555B..",
        "..57766310112223222113678985BB..",
        "...5435520002122321124748875D...",
        "....43675210023321224798A85C....",
        ".....586542000111124789995A.....",
        "......67475210001258888A59......",
        ".......69A853111348AA875A.......",
        "........46B984348AAA956C........",
        "...........587ABA6755...........",
        "................................"
    )

    private fun colorFor(ch: Char): Int {
        if (ch == '.') return Color.TRANSPARENT
        val index = when (ch) {
            in '0'..'9' -> ch - '0'
            in 'A'..'E' -> 10 + (ch - 'A')
            else -> 14
        }
        return palette[index]
    }

    fun bitmap(size: Int = 192): Bitmap {
        val tiny = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        for (y in 0 until 32) {
            for (x in 0 until 32) tiny.setPixel(x, y, colorFor(rows[y][x]))
        }
        return Bitmap.createScaledBitmap(tiny, size, size, false)
    }

    fun dataUrl(): String {
        val out = ByteArrayOutputStream()
        bitmap().compress(Bitmap.CompressFormat.PNG, 100, out)
        return "data:image/png;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }
}
