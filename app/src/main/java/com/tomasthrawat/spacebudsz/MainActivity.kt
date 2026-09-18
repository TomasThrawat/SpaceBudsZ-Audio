package com.tomasthrawat.spacebudsz

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Space
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import rikka.shizuku.Shizuku
import kotlin.math.PI
import kotlin.math.sin

class MainActivity : ComponentActivity() {

    private lateinit var audioManager: AudioManager

    private var track: AudioTrack? = null
    private var eq: Equalizer? = null
    private var bass: BassBoost? = null
    private var loudness: LoudnessEnhancer? = null
    private var route: AudioDeviceInfo? = null
    private var profile = Profile.SPACEBUDS

    private lateinit var deviceView: TextView
    private lateinit var routeView: TextView
    private lateinit var profileView: TextView
    private lateinit var volumeView: TextView
    private lateinit var shizukuView: TextView
    private lateinit var volumeBar: SeekBar

    private val sampleRate = 48_000

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        audioManager = getSystemService(AudioManager::class.java)

        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
                100
            )
        }

        buildUi()
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
        updateShizuku()
        updateVolume()
    }

    override fun onDestroy() {
        releaseAudio()
        super.onDestroy()
    }

    private fun buildUi() {
        val scroll = ScrollView(this)
        scroll.setBackgroundColor(color(R.color.bg))

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(28), dp(20), dp(28))
        }
        scroll.addView(root)

        root.addView(text("SpaceBuds Z Audio", 28f, true))
        root.addView(
            text(
                "تحكم في صوت الوسائط واختبار الصوت للهاتف وسماعات Bluetooth.",
                15f
            )
        )
        gap(root, 20)

        val deviceCard = card()
        deviceCard.addView(text("الجهاز المكتشف", 14f))
        deviceView = text("فحص...", 18f, true)
        deviceCard.addView(deviceView)
        deviceCard.addView(
            text("مخصص لاكتشاف Oraimo SpaceBuds Z OTW-625 عند اتصالها.", 13f)
        )
        root.addView(deviceCard)
        gap(root, 12)

        val routeCard = card()
        routeCard.addView(text("مخرج اختبار الصوت", 14f))
        routeView = text("تلقائي", 18f, true)
        routeCard.addView(routeView)

        val routes = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        addWeightButton(routes, "SpaceBuds Z") { chooseSpaceBuds() }
        addWeightButton(routes, "سماعة الهاتف") { choosePhone() }
        addWeightButton(routes, "تلقائي") { chooseAuto() }
        routeCard.addView(routes)
        root.addView(routeCard)
        gap(root, 12)

        val volumeCard = card()
        volumeCard.addView(text("صوت الوسائط", 14f))
        volumeView = text("", 18f, true)
        volumeCard.addView(volumeView)

        volumeBar = SeekBar(this)
        volumeCard.addView(volumeBar)
        volumeBar.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean
                ) {
                    volumeView.text =
                        progress.toString() + " / " + volumeBar.max.toString()
                    if (fromUser) {
                        audioManager.setStreamVolume(
                            AudioManager.STREAM_MUSIC,
                            progress,
                            0
                        )
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            }
        )
        root.addView(volumeCard)
        gap(root, 12)

        val profileCard = card()
        profileCard.addView(text("ملف الصوت", 14f))
        profileView = text(profile.label, 18f, true)
        profileCard.addView(profileView)

        val profiles = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        addWeightButton(profiles, "SpaceBuds Z") {
            setProfile(Profile.SPACEBUDS)
        }
        addWeightButton(profiles, "هاتف") {
            setProfile(Profile.PHONE)
        }
        addWeightButton(profiles, "Flat") {
            setProfile(Profile.FLAT)
        }
        profileCard.addView(profiles)
        root.addView(profileCard)
        gap(root, 12)

        val testCard = card()
        testCard.addView(text("اختبار الصوت", 14f))
        testCard.addView(
            text(
                "التحسين هنا مرتبط بصوت التطبيق نفسه، وليس بكل تطبيقات الهاتف.",
                13f
            )
        )

        val bassBar = SeekBar(this).apply {
            max = 1000
            progress = 450
        }
        testCard.addView(text("Bass", 13f))
        testCard.addView(bassBar)

        val loudBar = SeekBar(this).apply {
            max = 1200
            progress = 600
        }
        testCard.addView(text("Loudness", 13f))
        testCard.addView(loudBar)

        bassBar.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean
                ) {
                    if (fromUser) {
                        try {
                            bass?.setStrength(progress.toShort())
                        } catch (_: Throwable) {
                        }
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            }
        )

        loudBar.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean
                ) {
                    if (fromUser) {
                        try {
                            loudness?.setTargetGain(progress)
                        } catch (_: Throwable) {
                        }
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            }
        )

        testCard.addView(button("تشغيل اختبار 6 ثواني") {
            playTest(bassBar.progress, loudBar.progress)
        })
        testCard.addView(button("إيقاف") { stopTest() })
        root.addView(testCard)
        gap(root, 12)

        val systemCard = card()
        systemCard.addView(text("تحكم النظام", 14f))
        systemCard.addView(button("إعدادات الصوت") {
            startActivity(Intent(Settings.ACTION_SOUND_SETTINGS))
        })
        systemCard.addView(button("اختيار مخرج الصوت") {
            openOutputSwitcher()
        })
        root.addView(systemCard)
        gap(root, 12)

        val shizukuCard = card()
        shizukuCard.addView(text("Shizuku", 14f))
        shizukuView = text("", 17f, true)
        shizukuCard.addView(shizukuView)
        shizukuCard.addView(
            text(
                "يُستخدم للفحص فقط. لا توجد أوامر صوت داخلية غير موثقة.",
                13f
            )
        )
        root.addView(shizukuCard)

        setContentView(scroll)
        updateVolume()
        updateShizuku()
    }

    private fun refresh() {
        if (!::deviceView.isInitialized) return

        val outputs = try {
            audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList()
        } catch (_: Throwable) {
            emptyList()
        }

        val buds = outputs.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP &&
                it.productName.toString().contains("SpaceBuds", true)
        }

        val bluetooth = outputs.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
        }

        deviceView.text = when {
            buds != null -> "Oraimo SpaceBuds Z OTW-625 متصلة"
            bluetooth != null ->
                "Bluetooth: " + bluetooth.productName.toString()
            outputs.any {
                it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
            } -> "سماعة الهاتف"
            else -> "مخرج غير معروف"
        }
    }

    private fun chooseSpaceBuds() {
        route = findSpaceBuds()

        if (route == null) {
            Toast.makeText(
                this,
                "SpaceBuds Z مش متصلة حاليًا",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        routeView.text = "SpaceBuds Z"
        profile = Profile.SPACEBUDS
        profileView.text = profile.label
        applyRoute()
    }

    private fun choosePhone() {
        route = findPhoneSpeaker()

        if (route == null) {
            Toast.makeText(
                this,
                "سماعة الهاتف مش متاحة",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        routeView.text = "سماعة الهاتف"
        profile = Profile.PHONE
        profileView.text = profile.label
        applyRoute()
    }

    private fun chooseAuto() {
        route = null
        routeView.text = "تلقائي"
        applyRoute()
    }

    private fun findSpaceBuds(): AudioDeviceInfo? {
        return try {
            audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                .firstOrNull {
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP &&
                        it.productName.toString().contains("SpaceBuds", true)
                }
        } catch (_: Throwable) {
            null
        }
    }

    private fun findPhoneSpeaker(): AudioDeviceInfo? {
        return try {
            audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                .firstOrNull {
                    it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                }
        } catch (_: Throwable) {
            null
        }
    }

    private fun applyRoute() {
        try {
            track?.setPreferredDevice(route)
        } catch (_: Throwable) {
        }
    }

    private fun setProfile(value: Profile) {
        profile = value
        profileView.text = value.label

        if (track == null) {
            startAudio()
        }

        val effect = eq ?: return

        try {
            effect.enabled = true
            val count = effect.numberOfBands.toInt()
            val gains = when (value) {
                Profile.SPACEBUDS ->
                    floatArrayOf(450f, 250f, 50f, 150f, 350f)
                Profile.PHONE ->
                    floatArrayOf(0f, 100f, 200f, 350f, 200f)
                Profile.FLAT ->
                    floatArrayOf(0f, 0f, 0f, 0f, 0f)
            }

            val min = effect.bandLevelRange[0].toFloat()
            val max = effect.bandLevelRange[1].toFloat()

            for (band in 0 until count) {
                val level = gains[minOf(band, gains.lastIndex)]
                    .coerceIn(min, max)
                    .toInt()

                effect.setBandLevel(
                    band.toShort(),
                    level.toShort()
                )
            }
        } catch (_: Throwable) {
        }
    }

    private fun startAudio() {
        releaseAudio()

        val minBuffer = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        val format = AudioFormat.Builder()
            .setSampleRate(sampleRate)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
            .build()

        track = AudioTrack.Builder()
            .setAudioAttributes(attrs)
            .setAudioFormat(format)
            .setBufferSizeInBytes(maxOf(minBuffer, 24_000))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        applyRoute()

        try {
            val session = track!!.audioSessionId
            eq = Equalizer(0, session)
            bass = BassBoost(0, session)
            loudness = LoudnessEnhancer(session)

            eq?.enabled = true
            bass?.enabled = true
            loudness?.enabled = true

            setProfile(profile)
        } catch (_: Throwable) {
        }
    }

    private fun playTest(bassValue: Int, loudnessValue: Int) {
        startAudio()

        try {
            bass?.setStrength(bassValue.toShort())
            loudness?.setTargetGain(loudnessValue)
        } catch (_: Throwable) {
        }

        val seconds = 6
        val samples = sampleRate * seconds
        val data = ShortArray(samples * 2)

        for (i in 0 until samples) {
            val time = i.toDouble() / sampleRate.toDouble()
            val frequency =
                120.0 * Math.pow(12.0, time / seconds.toDouble())

            val value = (
                sin(2.0 * PI * frequency * time) *
                    0.24 *
                    Short.MAX_VALUE.toDouble()
                ).toInt().toShort()

            data[i * 2] = value
            data[i * 2 + 1] = value
        }

        track?.play()

        Thread {
            var offset = 0
            try {
                while (
                    offset < data.size &&
                    track?.playState == AudioTrack.PLAYSTATE_PLAYING
                ) {
                    val written = track?.write(
                        data,
                        offset,
                        minOf(4096, data.size - offset)
                    ) ?: 0

                    if (written <= 0) break
                    offset += written
                }
            } finally {
                runOnUiThread { stopTest() }
            }
        }.start()
    }

    private fun stopTest() {
        try {
            track?.pause()
            track?.flush()
        } catch (_: Throwable) {
        }
    }

    private fun openOutputSwitcher() {
        try {
            startActivity(
                Intent("android.settings.MEDIA_OUTPUT_SETTINGS")
            )
        } catch (_: Throwable) {
            startActivity(Intent(Settings.ACTION_SOUND_SETTINGS))
        }
    }

    private fun updateVolume() {
        if (!::volumeBar.isInitialized) return

        volumeBar.max =
            audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

        volumeBar.progress =
            audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

        volumeView.text =
            volumeBar.progress.toString() +
                " / " +
                volumeBar.max.toString()
    }

    private fun updateShizuku() {
        shizukuView.text =
            if (Shizuku.pingBinder()) "متصل وجاهز" else "غير متصل"
    }

    private fun releaseAudio() {
        try { track?.stop() } catch (_: Throwable) {}
        try { track?.release() } catch (_: Throwable) {}
        try { eq?.release() } catch (_: Throwable) {}
        try { bass?.release() } catch (_: Throwable) {}
        try { loudness?.release() } catch (_: Throwable) {}

        track = null
        eq = null
        bass = null
        loudness = null
    }

    private fun card(): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setBackgroundColor(color(R.color.surface))
        }

    private fun text(
        value: String,
        size: Float,
        bold: Boolean = false
    ): TextView =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color(R.color.text_primary))
            if (bold) {
                setTypeface(
                    typeface,
                    android.graphics.Typeface.BOLD
                )
            }
            setPadding(0, dp(4), 0, dp(4))
        }

    private fun button(
        label: String,
        action: () -> Unit
    ): TextView =
        TextView(this).apply {
            text = label
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(color(R.color.text_primary))
            setPadding(
                dp(12),
                dp(12),
                dp(12),
                dp(12)
            )
            setOnClickListener { action() }
        }

    private fun addWeightButton(
        row: LinearLayout,
        label: String,
        action: () -> Unit
    ) {
        row.addView(
            button(label, action),
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        )
    }

    private fun gap(root: LinearLayout, sizeDp: Int) {
        root.addView(
            Space(this),
            LinearLayout.LayoutParams(
                1,
                dp(sizeDp)
            )
        )
    }

    private fun color(id: Int): Int =
        ContextCompat.getColor(this, id)

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private enum class Profile(val label: String) {
        SPACEBUDS("SpaceBuds Z"),
        PHONE("هاتف"),
        FLAT("Flat")
    }
}
