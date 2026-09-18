package com.tomasthrawat.spacebudsz

import android.Manifest
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
import android.view.View
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

    private var testTrack: AudioTrack? = null
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null

    private var selectedProfile = Profile.SPACEBUDS
    private var preferredDevice: AudioDeviceInfo? = null

    private lateinit var deviceText: TextView
    private lateinit var shizukuText: TextView
    private lateinit var volumeText: TextView
    private lateinit var volumeSeek: SeekBar
    private lateinit var profileText: TextView
    private lateinit var routeText: TextView

    private val sampleRate = 48_000

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                20
            )
        }

        buildUi()
        refreshDevice()
    }

    override fun onResume() {
        super.onResume()
        refreshDevice()
        updateShizuku()
        updateVolume()
    }

    override fun onDestroy() {
        releaseEffects()
        super.onDestroy()
    }

    private fun buildUi() {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.bg))
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(28), dp(20), dp(28))
        }

        scroll.addView(root)

        root.addView(text("SpaceBuds Z Audio", 28f, R.color.text_primary, true))
        root.addView(
            text(
                getString(R.string.app_subtitle),
                15f,
                R.color.text_secondary
            )
        )

        addGap(root, 22)

        val deviceCard = card()
        deviceCard.addView(text("مخرج الصوت المتاح", 14f, R.color.text_secondary))
        deviceText = text("جاري الفحص...", 18f, R.color.text_primary, true)
        deviceCard.addView(deviceText)
        deviceCard.addView(
            text(
                "التطبيق بيحاول يميز SpaceBuds Z OTW-625 تلقائيًا.",
                13f,
                R.color.text_secondary
            )
        )
        root.addView(deviceCard)

        addGap(root, 12)

        val routeCard = card()
        routeCard.addView(text("مخرج اختبار الصوت", 14f, R.color.text_secondary))
        routeText = text("تلقائي", 18f, R.color.text_primary, true)
        routeCard.addView(routeText)

        val routeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        routeRow.addView(
            button("SpaceBuds Z") { selectSpaceBudsRoute() },
            weight = 1f
        )
        routeRow.addView(
            button("سماعة الهاتف") { selectPhoneRoute() },
            weight = 1f
        )
        routeRow.addView(
            button("تلقائي") { selectAutomaticRoute() },
            weight = 1f
        )

        routeCard.addView(routeRow)
        root.addView(routeCard)

        addGap(root, 12)

        val volumeCard = card()
        volumeCard.addView(text("صوت الوسائط", 14f, R.color.text_secondary))
        volumeText = text("", 18f, R.color.text_primary, true)
        volumeCard.addView(volumeText)

        volumeSeek = SeekBar(this).apply {
            max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            progress = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        }

        volumeSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(
                seekBar: SeekBar?,
                progress: Int,
                fromUser: Boolean
            ) {
                volumeText.text =
                    progress.toString() + " / " + volumeSeek.max.toString()

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
        })

        volumeCard.addView(volumeSeek)
        root.addView(volumeCard)

        addGap(root, 12)

        val profileCard = card()
        profileCard.addView(text("ملف الصوت", 14f, R.color.text_secondary))
        profileText = text(
            selectedProfile.label,
            18f,
            R.color.text_primary,
            true
        )
        profileCard.addView(profileText)

        val profileRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        profileRow.addView(
            button("SpaceBuds Z") {
                applyProfile(Profile.SPACEBUDS)
            },
            weight = 1f
        )
        profileRow.addView(
            button("هاتف") {
                applyProfile(Profile.PHONE)
            },
            weight = 1f
        )
        profileRow.addView(
            button("Flat") {
                applyProfile(Profile.FLAT)
            },
            weight = 1f
        )

        profileCard.addView(profileRow)
        root.addView(profileCard)

        addGap(root, 12)

        val testCard = card()
        testCard.addView(text("اختبار التحسين", 14f, R.color.text_secondary))
        testCard.addView(
            text(
                "الـEQ والـBass والـLoudness بيتطبقوا على جلسة الصوت اللي التطبيق نفسه بيشغلها.",
                13f,
                R.color.text_secondary
            )
        )

        val bassSeek = SeekBar(this).apply {
            max = 1000
            progress = 450
        }

        testCard.addView(text("Bass", 13f, R.color.text_secondary))
        testCard.addView(bassSeek)

        val loudSeek = SeekBar(this).apply {
            max = 1200
            progress = 600
        }

        testCard.addView(text("Loudness", 13f, R.color.text_secondary))
        testCard.addView(loudSeek)

        bassSeek.setOnSeekBarChangeListener(
            effectListener { value ->
                try {
                    bassBoost?.setStrength(value)
                } catch (_: Throwable) {
                }
            }
        )

        loudSeek.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean
                ) {
                    if (fromUser) {
                        try {
                            loudnessEnhancer?.setTargetGain(progress.toFloat())
                        } catch (_: Throwable) {
                        }
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            }
        )

        testCard.addView(
            button("تشغيل اختبار صوتي") {
                playTest(
                    bassSeek.progress,
                    loudSeek.progress
                )
            }
        )

        testCard.addView(
            button("إيقاف") {
                stopTest()
            }
        )

        root.addView(testCard)

        addGap(root, 12)

        val systemCard = card()
        systemCard.addView(text("تحكم النظام", 14f, R.color.text_secondary))

        systemCard.addView(
            button("فتح إعدادات الصوت") {
                startActivity(
                    android.content.Intent(
                        Settings.ACTION_SOUND_SETTINGS
                    )
                )
            }
        )

        systemCard.addView(
            button("فتح اختيار مخرج الصوت") {
                openOutputSwitcher()
            }
        )

        root.addView(systemCard)

        addGap(root, 12)

        val shizukuCard = card()
        shizukuCard.addView(text("Shizuku", 14f, R.color.text_secondary))
        shizukuText = text("", 17f, R.color.text_primary, true)
        shizukuCard.addView(shizukuText)

        shizukuCard.addView(
            text(
                "التطبيق بيستخدم Shizuku للفحص فقط، ومش بيشغل أوامر صوت داخلية غير موثقة.",
                13f,
                R.color.text_secondary
            )
        )

        root.addView(shizukuCard)

        setContentView(scroll)
        updateShizuku()
        updateVolume()
    }

    private fun refreshDevice() {
        if (!::deviceText.isInitialized) return

        val outputs = try {
            audioManager
                .getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                .toList()
        } catch (_: SecurityException) {
            emptyList()
        }

        val earbuds = outputs.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP &&
                it.productName.toString()
                    .contains("SpaceBuds", ignoreCase = true)
        }

        val anyBluetooth = outputs.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
        }

        deviceText.text = when {
            earbuds != null ->
                "Oraimo SpaceBuds Z OTW-625 متصلة"
            anyBluetooth != null ->
                "Bluetooth: " + anyBluetooth.productName.toString()
            outputs.any {
                it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
            } ->
                "سماعة الهاتف"
            else ->
                "مخرج صوت غير معروف"
        }

        if (preferredDevice != null && !outputs.contains(preferredDevice)) {
            preferredDevice = null
            routeText.text = "تلقائي"
        }

        if (earbuds != null && preferredDevice == null) {
            preferredDevice = earbuds
            routeText.text = "SpaceBuds Z"
            selectedProfile = Profile.SPACEBUDS
            profileText.text = selectedProfile.label
        }
    }

    private fun findSpaceBuds(): AudioDeviceInfo? {
        val outputs = try {
            audioManager
                .getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                .toList()
        } catch (_: SecurityException) {
            emptyList()
        }

        return outputs.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP &&
                it.productName.toString()
                    .contains("SpaceBuds", ignoreCase = true)
        }
    }

    private fun findPhoneSpeaker(): AudioDeviceInfo? {
        val outputs = try {
            audioManager
                .getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                .toList()
        } catch (_: SecurityException) {
            emptyList()
        }

        return outputs.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
        }
    }

    private fun selectSpaceBudsRoute() {
        val earbuds = findSpaceBuds()

        if (earbuds == null) {
            Toast.makeText(
                this,
                "SpaceBuds Z مش متاحة حاليًا",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        preferredDevice = earbuds
        routeText.text = "SpaceBuds Z"
        selectedProfile = Profile.SPACEBUDS
        profileText.text = selectedProfile.label

        if (testTrack != null) {
            applyPreferredDevice()
        }
    }

    private fun selectPhoneRoute() {
        val speaker = findPhoneSpeaker()

        if (speaker == null) {
            Toast.makeText(
                this,
                "سماعة الهاتف مش متاحة",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        preferredDevice = speaker
        routeText.text = "سماعة الهاتف"
        selectedProfile = Profile.PHONE
        profileText.text = selectedProfile.label

        if (testTrack != null) {
            applyPreferredDevice()
        }
    }

    private fun selectAutomaticRoute() {
        preferredDevice = null
        routeText.text = "تلقائي"
        if (testTrack != null) {
            applyPreferredDevice()
        }
    }

    private fun applyPreferredDevice() {
        val track = testTrack ?: return

        try {
            val ok = track.setPreferredDevice(preferredDevice)
            if (!ok && preferredDevice != null) {
                Toast.makeText(
                    this,
                    "Android رفض اختيار مخرج الاختبار",
                    Toast.LENGTH_SHORT
                ).show()
            }
        } catch (_: Throwable) {
        }
    }

    private fun updateShizuku() {
        shizukuText.text =
            if (Shizuku.pingBinder()) "متصل وجاهز" else "غير متصل"
    }

    private fun updateVolume() {
        if (!::volumeSeek.isInitialized) return

        volumeSeek.max =
            audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

        volumeSeek.progress =
            audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

        volumeText.text =
            volumeSeek.progress.toString() +
                " / " +
                volumeSeek.max.toString()
    }

    private fun applyProfile(profile: Profile) {
        selectedProfile = profile
        profileText.text = profile.label

        if (testTrack == null) {
            startAudioSession()
        }

        val eq = equalizer ?: return

        try {
            eq.enabled = true

            val count = eq.numberOfBands.toInt()
            val gains = when (profile) {
                Profile.SPACEBUDS ->
                    floatArrayOf(450f, 250f, 50f, 150f, 350f)
                Profile.PHONE ->
                    floatArrayOf(0f, 100f, 200f, 350f, 200f)
                Profile.FLAT ->
                    floatArrayOf(0f, 0f, 0f, 0f, 0f)
            }

            val min = eq.bandLevelRange[0].toFloat()
            val max = eq.bandLevelRange[1].toFloat()

            for (band in 0 until count) {
                val gain = gains[
                    minOf(band, gains.lastIndex)
                ].coerceIn(min, max)

                eq.setBandLevel(
                    band.toShort(),
                    gain.toInt().toShort()
                )
            }
        } catch (_: Throwable) {
        }
    }

    private fun startAudioSession() {
        releaseEffects()

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

        testTrack = AudioTrack.Builder()
            .setAudioAttributes(attrs)
            .setAudioFormat(format)
            .setBufferSizeInBytes(maxOf(minBuffer, 24_000))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        applyPreferredDevice()

        val session = testTrack!!.audioSessionId

        try {
            equalizer = Equalizer(0, session)
            bassBoost = BassBoost(0, session)
            loudnessEnhancer = LoudnessEnhancer(session)

            equalizer?.enabled = true
            bassBoost?.enabled = true
            loudnessEnhancer?.enabled = true

            applyProfile(selectedProfile)
        } catch (_: Throwable) {
        }
    }

    private fun playTest(bass: Int, loudness: Int) {
        startAudioSession()

        try {
            bassBoost?.setStrength(bass.toShort())
            loudnessEnhancer?.setTargetGain(loudness.toFloat())
        } catch (_: Throwable) {
        }

        val seconds = 6
        val samples = sampleRate * seconds
        val buffer = ShortArray(samples * 2)

        for (i in 0 until samples) {
            val time = i.toDouble() / sampleRate.toDouble()
            val sweep =
                120.0 * Math.pow(12.0, time / seconds.toDouble())

            val value = (
                sin(2.0 * PI * sweep * time) *
                    0.24 *
                    Short.MAX_VALUE.toDouble()
                )
                .toInt()
                .toShort()

            buffer[i * 2] = value
            buffer[i * 2 + 1] = value
        }

        testTrack?.play()

        Thread {
            try {
                var offset = 0

                while (
                    offset < buffer.size &&
                    testTrack?.playState ==
                    AudioTrack.PLAYSTATE_PLAYING
                ) {
                    val written = testTrack?.write(
                        buffer,
                        offset,
                        minOf(4096, buffer.size - offset)
                    ) ?: 0

                    if (written <= 0) break
                    offset += written
                }
            } finally {
                runOnUiThread {
                    stopTest()
                }
            }
        }.start()
    }

    private fun stopTest() {
        try {
            testTrack?.pause()
            testTrack?.flush()
        } catch (_: Throwable) {
        }
    }

    private fun openOutputSwitcher() {
        try {
            startActivity(
                android.content.Intent(
                    "android.settings.MEDIA_OUTPUT_SETTINGS"
                )
            )
        } catch (_: Exception) {
            startActivity(
                android.content.Intent(
                    Settings.ACTION_SOUND_SETTINGS
                )
            )
        }
    }

    private fun releaseEffects() {
        try { testTrack?.stop() } catch (_: Throwable) {}
        try { testTrack?.release() } catch (_: Throwable) {}
        try { equalizer?.release() } catch (_: Throwable) {}
        try { bassBoost?.release() } catch (_: Throwable) {}
        try { loudnessEnhancer?.release() } catch (_: Throwable) {}

        testTrack = null
        equalizer = null
        bassBoost = null
        loudnessEnhancer = null
    }

    private fun effectListener(
        action: (Short) -> Unit
    ): SeekBar.OnSeekBarChangeListener =
        object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(
                seekBar: SeekBar?,
                progress: Int,
                fromUser: Boolean
            ) {
                if (fromUser) {
                    action(progress.toShort())
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        }

    private fun card(): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                dp(16),
                dp(16),
                dp(16),
                dp(16)
            )
            setBackgroundColor(
                ContextCompat.getColor(
                    this@MainActivity,
                    R.color.surface
                )
            )
        }

    private fun text(
        value: String,
        size: Float,
        colorRes: Int,
        bold: Boolean = false
    ): TextView =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(
                ContextCompat.getColor(
                    this@MainActivity,
                    colorRes
                )
            )
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
            setTextColor(
                ContextCompat.getColor(
                    this@MainActivity,
                    R.color.text_primary
                )
            )
            gravity = Gravity.CENTER
            setPadding(
                dp(12),
                dp(12),
                dp(12),
                dp(12)
            )
            setOnClickListener { action() }
        }

    private fun LinearLayout.addView(
        view: View,
        weight: Float
    ) {
        addView(
            view,
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                weight
            )
        )
    }

    private fun addGap(
        root: LinearLayout,
        valueDp: Int
    ) {
        root.addView(
            Space(this),
            LinearLayout.LayoutParams(
                1,
                dp(valueDp)
            )
        )
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private enum class Profile(
        val label: String
    ) {
        SPACEBUDS("SpaceBuds Z"),
        PHONE("هاتف"),
        FLAT("Flat")
    }
}
