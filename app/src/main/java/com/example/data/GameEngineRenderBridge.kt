package com.example.data

/**
 * Render bridge kept in the app module: the ASCII perspective renderer lives in :core:render,
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
