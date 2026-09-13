package com.calcvault.storage.provider

import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * StorageModeWarningView
 *
 * Persistent warning banner shown at the top of the vault UI
 * when running in Local mode (USB not connected).
 *
 * Design:
 *  - Amber/orange banner with ⚠️ icon
 *  - "Running in local mode — connect USB for full security"
 *  - Tap to show details dialog
 *  - Auto-dismisses when USB is connected (switches to green confirmation)
 *  - Never fully dismissable by user — security info must be visible
 */
class StorageModeWarningView(private val context: Context) {

    private var bannerView: LinearLayout? = null
    private var tvText: TextView? = null

    /**
     * Create the warning banner view.
     * Attach it to a FrameLayout root as the topmost child.
     */
    fun createBanner(root: FrameLayout, onTap: () -> Unit): View {
        val banner = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(0xFFFF8C00.toInt()) // amber warning
            setPadding(24, 14, 24, 14)
            elevation = 8f
        }

        val icon = TextView(context).apply {
            text = "⚠️"
            textSize = 16f
            setPadding(0, 0, 12, 0)
            banner.addView(this)
        }

        val message = TextView(context).apply {
            text = "Local mode — connect USB for full security"
            textSize = 13f
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            banner.addView(this)
        }
        tvText = message

        val arrow = TextView(context).apply {
            text = "›"; textSize = 18f; setTextColor(Color.WHITE); setPadding(8, 0, 0, 0)
            banner.addView(this)
        }

        banner.setOnClickListener { onTap() }

        val lp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.TOP
        )
        root.addView(banner, lp)
        bannerView = banner
        return banner
    }

    /**
     * Switch banner to USB connected (green confirmation).
     * Auto-hides after 3 seconds.
     */
    fun showUSBConnected() {
        bannerView?.apply {
            setBackgroundColor(0xFF2ECC71.toInt()) // green
            tvText?.text = "✓ USB connected — fully secure"
            // Fade out after 3 seconds
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                ObjectAnimator.ofFloat(this, View.ALPHA, 1f, 0f).apply {
                    duration = 600
                    addListener(object : android.animation.AnimatorListenerAdapter() {
                        override fun onAnimationEnd(a: android.animation.Animator) {
                            visibility = View.GONE
                        }
                    })
                    start()
                }
            }, 3_000)
        }
    }

    /**
     * Restore amber warning banner (USB disconnected).
     */
    fun showLocalModeWarning() {
        bannerView?.apply {
            alpha = 1f
            visibility = View.VISIBLE
            setBackgroundColor(0xFFFF8C00.toInt())
            tvText?.text = "Local mode — connect USB for full security"
        }
    }

    /**
     * Show a detail dialog explaining the security difference.
     */
    fun showDetailDialog(context: Context) {
        android.app.AlertDialog.Builder(context)
            .setTitle("⚠️ Local Mode — Reduced Security")
            .setMessage(
                """
                You are currently running without a USB drive.

                In local mode:
                • Messages are stored on your phone
                • A rooted device could access your data
                • If your phone is seized, forensic tools may extract data

                In USB mode:
                • All data lives on your USB drive
                • Removing the USB instantly secures all data
                • No data stored on the phone at all

                To switch to full security:
                • Insert your USB drive via OTG adapter
                • The app will automatically migrate your data to USB
                • Local data will be securely wiped after migration

                You can continue using the app in local mode, but
                USB mode is strongly recommended for sensitive conversations.
                """.trimIndent()
            )
            .setPositiveButton("Understood") { d, _ -> d.dismiss() }
            .setNegativeButton("Connect USB") { _, _ ->
                // Could launch a USB setup guide here
            }
            .show()
    }
}
