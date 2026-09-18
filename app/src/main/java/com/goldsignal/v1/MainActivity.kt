package com.goldsignal.v1

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.widget.*

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 35, 28, 28)
            setBackgroundColor(Color.rgb(18,18,20))
        }

        fun tv(text: String, size: Float, color: Int = Color.WHITE): TextView =
            TextView(this).apply {
                this.text = text; textSize = size; setTextColor(color)
                setPadding(0, 8, 0, 8)
            }

        root.addView(tv("GOLD SIGNAL V1", 28f, Color.rgb(212,175,55)))
        root.addView(tv("توصيات XAU/USD — نسخة الاختبار", 16f))
        root.addView(tv("السعر الحالي: —", 21f))
        root.addView(tv("الاتجاه: محايد", 18f))

        val signal = tv("⚪  NO TRADE\n\nلا توجد بيانات سوق متصلة حالياً", 25f, Color.LTGRAY).apply {
            gravity = Gravity.CENTER
            setPadding(15, 35, 15, 35)
        }
        root.addView(signal)

        root.addView(tv("Entry: —\nStop Loss: —\nTP1: —\nTP2: —\nقوة الإشارة: —/100", 18f))

        val note = tv(
            "المحرك في V1 يستخدم EMA 20/50/200 + RSI + MACD + ATR + دعم/مقاومة.\n" +
            "هذه النسخة لا تنفذ صفقات ولا تضمن الأرباح. سيتم ربط بيانات الذهب الحية في الخطوة التالية.",
            14f, Color.LTGRAY
        )
        root.addView(note)

        val btn = Button(this).apply {
            text = "تحديث التحليل"
            setOnClickListener {
                Toast.makeText(this@MainActivity, "V1 جاهزة لربط بيانات XAU/USD", Toast.LENGTH_SHORT).show()
            }
        }
        root.addView(btn)
        setContentView(root)
    }
}
