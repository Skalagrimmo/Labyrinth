package com.example.data

import com.example.core.model.CellType
import com.example.core.model.CyberWeather
import com.example.core.model.Direction

/**
 * Render bridge in :core:render (package com.example.data preserved for app call sites):
 * which depends on :core:engine — the engine facade must not depend back on it.
 * The extension preserves the GameEngine.render3DPerspective(..) call syntax for app code.
 */
fun GameEngine.render3DPerspective(
    grid: Array<Array<CellType>>,
    px: Int,
    py: Int,
    dir: Direction,
    activeWeather: CyberWeather = CyberWeather.CLEAR
): String =
    AsciiPerspectiveRenderer.render(
        grid = grid,
        px = px,
        py = py,
        dir = dir,
        activeWeather = activeWeather
    )
