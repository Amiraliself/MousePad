package com.example.mousepad

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var editBtn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.gravity = Gravity.CENTER_HORIZONTAL
        root.setPadding(48, 120, 48, 48)

        status = TextView(this)
        status.textSize = 18f
        status.setPadding(0, 0, 0, 48)
        root.addView(status)

        val accBtn = Button(this)
        accBtn.text = "1) روشن کردن سرویس در دسترس‌پذیری"
        accBtn.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        root.addView(accBtn)

        val batBtn = Button(this)
        batBtn.text = "2) استثنای باتری"
        batBtn.setOnClickListener {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
        root.addView(batBtn)

        editBtn = Button(this)
        editBtn.setOnClickListener {
            val s = MouseService.instance
            if (s == null) {
                Toast.makeText(this, "اول سرویس رو روشن کن", Toast.LENGTH_SHORT).show()
            } else {
                s.setEditMode(!s.editMode)
                refresh()
            }
        }
        root.addView(editBtn)

        val help = TextView(this)
        help.textSize = 15f
        help.setPadding(0, 48, 0, 0)
        help.text = "راهنما:\n" +
            "حالت ویرایش روشنه: قاب تاچ‌پد دیده می‌شه. با کشیدن وسط قاب جابه‌جاش کن و با کشیدن گوشه‌ی پایین راست اندازه‌اش رو عوض کن.\n" +
            "حالت ویرایش خاموشه: تاچ‌پد کاملا شفافه و توی ضبط صفحه دیده نمی‌شه.\n" +
            "تپ: کلیک. نگه داشتن: کلیک طولانی. دو انگشت: اسکرول.\n" +
            "سوایپ از لبه‌ی چپ تاچ‌پد: بک. از لبه‌ی پایین: هوم. از لبه‌ی راست: ریسنت."
        root.addView(help)

        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val s = MouseService.instance
        status.text = if (s == null) "وضعیت: سرویس خاموشه" else "وضعیت: سرویس روشنه"
        editBtn.text = if (s != null && s.editMode) "3) خاموش کردن حالت ویرایش" else "3) روشن کردن حالت ویرایش"
    }
}
