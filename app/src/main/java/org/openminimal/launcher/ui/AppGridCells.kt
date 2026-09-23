package org.openminimal.launcher.ui

import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp

/** Honor the requested count while keeping every icon target at least 48 dp wide. */
internal data class AppGridCells(private val columns: Int) : GridCells {
    override fun Density.calculateCrossAxisCellSizes(availableSize: Int, spacing: Int): List<Int> {
        val fit = ((availableSize + spacing) / (48.dp.roundToPx() + spacing)).coerceAtLeast(1)
        val count = columns.coerceIn(1, fit)
        val total = (availableSize - spacing * (count - 1)).coerceAtLeast(0)
        return List(count) { total / count + if (it < total % count) 1 else 0 }
    }
}
