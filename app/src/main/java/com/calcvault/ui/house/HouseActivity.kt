package com.calcvault.ui.house

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.calcvault.house.DualCompanionHouseActivity

class HouseActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(this, DualCompanionHouseActivity::class.java))
        finish()
    }
}
