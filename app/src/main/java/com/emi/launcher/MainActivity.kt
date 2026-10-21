package com.emi.launcher

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * Launcher minimalista para Android TV.
 *
 * Una sola Activity, sin librerías externas, sin red, sin animaciones.
 * Toda la UI se construye con Views clásicas sobre un layout de dos filas.
 */
class MainActivity : Activity() {

    private companion object {
        const val PREFS_NAME = "launcher"
        const val KEY_HDMI_INPUT_ID = "hdmi_input_id"
        const val ICON_SIZE_DP = 88
        const val LABEL_SIZE_SP = 22f
        const val TILE_MARGIN_DP = 12
        const val TILE_PADDING_DP = 20
    }

    private val prefs: SharedPreferences by lazy {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private var firstBuild = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }

    override fun onStart() {
        super.onStart()
        // Se reconstruye en cada onStart por si se instaló/desinstaló alguna app.
        // Son solo 5 consultas al PackageManager: es barato y mantiene la lista fresca.
        buildGrid()
    }

    // ---------------------------------------------------------------------
    // Construcción de la cuadrícula
    // ---------------------------------------------------------------------

    private fun buildGrid() {
        val rowTop = findViewById<LinearLayout>(R.id.row_top)
        val rowBottom = findViewById<LinearLayout>(R.id.row_bottom)
        rowTop.removeAllViews()
        rowBottom.removeAllViews()

        val tiles = mutableListOf<View>()

        // 1. PlayStation (HDMI 1): siempre visible, es el tile destacado.
        tiles += makeTile(
            label = getString(R.string.btn_playstation),
            iconDrawable = getDrawable(R.drawable.ic_gamepad),
            accent = true,
            onClick = { launchHdmi() },
            onLongClick = { chooseHdmiInput(forget = true); true }
        )

        // 2-5. Apps de streaming: se ocultan si no están instaladas.
        val apps = listOf(
            listOf("com.netflix.ninja"),
            listOf("com.amazon.amazonvideo.livingroom", "com.amazon.avod.thirdpartyclient"),
            listOf("com.google.android.youtube.tv"),
            listOf("com.wbd.stream", "com.hbo.hbonow", "com.hbomax.hbomax")
        )
        for (candidates in apps) {
            val pkg = candidates.firstOrNull { isInstalled(it) } ?: continue
            val info = packageManager.getApplicationInfo(pkg, 0)
            tiles += makeTile(
                label = info.loadLabel(packageManager).toString(),
                iconDrawable = info.loadIcon(packageManager),
                accent = false,
                onClick = { launchApp(pkg) },
                onLongClick = null
            )
        }

        // 6. Ajustes del sistema.
        tiles += makeTile(
            label = getString(R.string.btn_settings),
            iconDrawable = getDrawable(R.drawable.ic_settings),
            accent = false,
            onClick = {
                try {
                    startActivity(Intent(Settings.ACTION_SETTINGS))
                } catch (e: ActivityNotFoundException) {
                    toast(getString(R.string.no_fallback))
                }
            },
            onLongClick = null
        )

        // Repartir en dos filas: la mitad superior y la inferior.
        // El orden de foco queda dado por el orden de inserción (izq→der, arriba→abajo).
        val topCount = (tiles.size + 1) / 2
        tiles.forEachIndexed { i, tile ->
            (if (i < topCount) rowTop else rowBottom).addView(tile)
        }
        rowBottom.visibility = if (tiles.size > topCount) View.VISIBLE else View.GONE

        if (firstBuild) {
            firstBuild = false
            tiles.first().requestFocus()
        }
    }

    private fun makeTile(
        label: String,
        iconDrawable: android.graphics.drawable.Drawable?,
        accent: Boolean,
        onClick: () -> Unit,
        onLongClick: (() -> Boolean)?
    ): View {
        val tile = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            isFocusable = true
            isClickable = true
            setBackgroundResource(
                if (accent) R.drawable.tile_background_accent else R.drawable.tile_background
            )
            val pad = dp(TILE_PADDING_DP)
            setPadding(pad, pad, pad, pad)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                val m = dp(TILE_MARGIN_DP)
                setMargins(m, 0, m, 0)
            }
            setOnClickListener { onClick() }
            if (onLongClick != null) setOnLongClickListener { onLongClick() }
        }

        val icon = ImageView(this).apply {
            setImageDrawable(iconDrawable)
            layoutParams = LinearLayout.LayoutParams(dp(ICON_SIZE_DP), dp(ICON_SIZE_DP))
        }

        val text = TextView(this).apply {
            text = label
            setTextColor(getColor(R.color.text_primary))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, LABEL_SIZE_SP)
            gravity = Gravity.CENTER
            maxLines = 1
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(12) }
        }

        tile.addView(icon)
        tile.addView(text)
        return tile
    }

    // ---------------------------------------------------------------------
    // Apps
    // ---------------------------------------------------------------------

    private fun isInstalled(pkg: String): Boolean = try {
        packageManager.getApplicationInfo(pkg, 0).enabled
    } catch (e: Exception) {
        false
    }

    private fun launchApp(pkg: String) {
        val intent = packageManager.getLeanbackLaunchIntentForPackage(pkg)
            ?: packageManager.getLaunchIntentForPackage(pkg)
        if (intent != null) {
            try {
                startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                // La app pudo haberse desinstalado desde que se armó la cuadrícula.
                buildGrid()
            }
        } else {
            buildGrid()
        }
    }

    // ---------------------------------------------------------------------
    // HDMI (PlayStation)
    // ---------------------------------------------------------------------

    private fun tvInputs(): List<TvInputInfo> {
        val tim = getSystemService(TV_INPUT_SERVICE) as? TvInputManager ?: return emptyList()
        return tim.tvInputList.filter { it.isPassthroughInput }
    }

    private fun launchHdmi() {
        // 1. Si el usuario ya eligió una entrada, usarla directamente.
        prefs.getString(KEY_HDMI_INPUT_ID, null)?.let { saved ->
            if (openInput(saved)) return
            // La entrada guardada ya no funciona: olvidarla y volver a resolver.
            prefs.edit().remove(KEY_HDMI_INPUT_ID).apply()
        }

        val inputs = tvInputs()
        if (inputs.isEmpty()) {
            toast(getString(R.string.no_inputs))
            openSystemInputPicker()
            return
        }

        // 2. Intentar identificar la HDMI 1 con seguridad por etiqueta o id.
        val hdmi1 = inputs.filter { looksLikeHdmi1(it) }
        if (hdmi1.size == 1) {
            val id = hdmi1[0].id
            if (openInput(id)) {
                prefs.edit().putString(KEY_HDMI_INPUT_ID, id).apply()
                return
            }
        }

        // 3. No hay identificación segura: que el usuario elija de la lista.
        chooseHdmiInput(forget = false)
    }

    /** Heurística: la etiqueta o el id contienen "HDMI 1" / "HDMI1" / "HDMI_1". */
    private fun looksLikeHdmi1(info: TvInputInfo): Boolean {
        val label = info.loadLabel(this)?.toString().orEmpty()
        val haystack = "$label ${info.id}".uppercase()
            .replace("_", " ")
            .replace("-", " ")
        // Evitar que "HDMI 10" cuente como "HDMI 1".
        val regex = Regex("HDMI ?1(?![0-9])")
        return regex.containsMatchIn(haystack)
    }

    /**
     * Muestra la lista de entradas passthrough para que el usuario elija cuál es
     * la PlayStation. La elección se guarda en SharedPreferences.
     * Con [forget] en true (pulsación larga) se descarta la elección anterior.
     */
    private fun chooseHdmiInput(forget: Boolean) {
        if (forget) prefs.edit().remove(KEY_HDMI_INPUT_ID).apply()

        val inputs = tvInputs()
        if (inputs.isEmpty()) {
            toast(getString(R.string.no_inputs))
            openSystemInputPicker()
            return
        }

        val labels = inputs.map { info ->
            info.loadLabel(this)?.toString().takeUnless { it.isNullOrBlank() } ?: info.id
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle(R.string.choose_input)
            .setItems(labels) { _, which ->
                val id = inputs[which].id
                if (openInput(id)) {
                    prefs.edit().putString(KEY_HDMI_INPUT_ID, id).apply()
                    toast(getString(R.string.input_saved))
                } else {
                    toast(getString(R.string.open_input_failed))
                    openSystemInputPicker()
                }
            }
            .show()
    }

    /** Abre una entrada passthrough vía la app de TV en vivo. Devuelve false si falló. */
    private fun openInput(inputId: String): Boolean = try {
        val uri = TvContract.buildChannelUriForPassthroughInput(inputId)
        startActivity(Intent(Intent.ACTION_VIEW, uri))
        true
    } catch (e: Exception) {
        false
    }

    /** Último recurso: abrir la app de TV en vivo o, si no existe, los Ajustes. */
    private fun openSystemInputPicker() {
        val fallbacks = listOf(
            Intent(Intent.ACTION_VIEW, TvContract.Channels.CONTENT_URI),
            Intent(Settings.ACTION_SETTINGS)
        )
        for (intent in fallbacks) {
            try {
                startActivity(intent)
                return
            } catch (e: Exception) {
                // probar el siguiente
            }
        }
        toast(getString(R.string.no_fallback))
    }

    // ---------------------------------------------------------------------
    // Utilidades
    // ---------------------------------------------------------------------

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
