package io.github.koalaplot.core.bar

import androidx.compose.runtime.Stable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import io.github.koalaplot.core.util.rad
import io.github.koalaplot.core.util.toDegrees
import io.github.koalaplot.core.xygraph.AxisModel
import io.github.koalaplot.core.xygraph.XYGraphScope
import kotlin.math.asin
import kotlin.math.max

/**
 * Rectangle shape with convex shaped side.
 * Useful for Single Vertical Bar Plot rendering.
 * Use in Stacked Bars is discouraged.
 */
@Stable
private val PlaneConvexShape: Shape = object : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val shapeWidth = size.width
        val shapeHeight = size.height
        val arcRadius = shapeWidth / 2

        return Path().apply {
            val rectHeight = max((shapeHeight - arcRadius), 0F)
            addRect(
                rect = Rect(
                    offset = Offset(0F, arcRadius),
                    size = Size(shapeWidth, rectHeight)
                )
            )

            val heightRadiusOffset = max((arcRadius - shapeHeight), 0F)
            val heightRadiusOffsetDegrees =
                asin(heightRadiusOffset / arcRadius).rad.toDegrees().value.toFloat()
            addArc(
                oval = Size(shapeWidth, shapeWidth).toRect(),
                startAngleDegrees = 180F + heightRadiusOffsetDegrees,
                sweepAngleDegrees = 180F - 2 * heightRadiusOffsetDegrees
            )
        }.let(Outline::Generic)
    }
}

/**
 * Rectangle shape with convex shaped sides.
 * Useful for Single Vertical Bar Plot rendering.
 * Use in Stacked Bars is discouraged.
 */
@Stable
private val BiConvexShape: Shape = object : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val outline = PlaneConvexShape.createOutline(size, layoutDirection, density) as Outline.Generic

        val shapeWidth = size.width
        val shapeHeight = size.height
        val arcRadius = shapeWidth / 2

        val cutoutRect = Path().apply {
            addRect(
                rect = Rect(
                    offset = Offset(0F, shapeHeight - arcRadius),
                    size = Size(shapeWidth, shapeWidth)
                )
            )
        }
        val cutoutArc = Path().apply {
            addArc(
                oval = Rect(
                    offset = Offset(0F, shapeHeight - shapeWidth),
                    size = Size(shapeWidth, shapeWidth)
                ),
                startAngleDegrees = 0F,
                sweepAngleDegrees = 180F
            )
        }
        val cutout = (cutoutRect - cutoutArc)
        return (outline.path - cutout).let(Outline::Generic)
    }
}

/**
 * Rectangle shape with concave/convex shaped sides.
 * Useful for Single Vertical Bar and Stacked Bars Plot rendering.
 *
 * @param xyGraphScope Provides access to [yAxisModel] and acts as an implementation of [XYGraphScope].
 * @param value The [VerticalBarPlotEntry] that defines the cutouts for the [ConcaveConvexShape].
 */
@Stable
public class ConcaveConvexShape<X, E : VerticalBarPlotEntry<X, Float>>(
    private val xyGraphScope: XYGraphScope<X, Float>,
    private val index: Int,
    private val value: E
) : Shape, XYGraphScope<X, Float> by xyGraphScope {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val shapeWidth = size.width
        val shapeHeight = size.height
        val arcRadius = shapeWidth / 2

        // Rendering negative values
        val isInverted = value.y.yMax < value.y.yMin
        // Required for proper bar rendering in waterfall charts
        if (index == 0) {
            val outline = PlaneConvexShape.createOutline(size, layoutDirection, density) as Outline.Generic

            outline.path.apply {
                // Rendering bar in negative direction
                if (isInverted) {
                    inverted(pivotX = shapeWidth / 2F, pivotY = shapeHeight / 2F)
                }
            }
            return outline
        }

        val (yZeroOffset, yMinOffset, yMaxOffset) = yAxisModel.yOffsets(value.y.yMin, value.y.yMax)

        // Prevent division by zero
        if (yMaxOffset == yMinOffset) return Path().let(Outline::Generic)

        val heightOffsetRatio = size.height / (yMaxOffset - yMinOffset)
        val offsetToHeight = { offset: Float -> offset * heightOffsetRatio }

        val yMinZeroOffset = yMinOffset - yZeroOffset
        val yMaxZeroOffset = yMaxOffset - yZeroOffset

        val yMinZeroHeight = offsetToHeight(yMinZeroOffset)
        val yMaxZeroHeight = offsetToHeight(yMaxZeroOffset)

        val yMinZeroArcHeight = max((arcRadius - yMinZeroHeight), 0F)
        val yMaxZeroArcHeight = max((arcRadius - yMaxZeroHeight), 0F)

        val yMaxZeroArcHeightDegrees =
            asin(yMaxZeroArcHeight / arcRadius).rad.toDegrees().value.toFloat()

        return Path().apply {
            (
                Path().apply {
                    addArc(
                        oval = Size(shapeWidth, shapeWidth).toRect(),
                        startAngleDegrees = 180F + yMaxZeroArcHeightDegrees,
                        sweepAngleDegrees = 180F - 2 * yMaxZeroArcHeightDegrees
                    )
                } - Path().apply {
                    addArc(
                        oval = Rect(
                            offset = Offset(0F, shapeHeight),
                            size = Size(shapeWidth, shapeWidth)
                        ),
                        startAngleDegrees = 180F,
                        sweepAngleDegrees = 180F
                    )
                }
                ).let(::addPath)

            (
                Path().apply {
                    addRect(
                        rect = Rect(
                            offset = Offset(0F, arcRadius),
                            size = Size(shapeWidth, max(shapeHeight - yMinZeroArcHeight, 0F))
                        )
                    )
                } - Path().apply {
                    addArc(
                        oval = Rect(
                            offset = Offset(0F, shapeHeight),
                            size = Size(shapeWidth, shapeWidth)
                        ),
                        startAngleDegrees = 180F,
                        sweepAngleDegrees = 180F
                    )
                }
                ).let(::addPath)
            // Rendering bar in negative direction
            if (isInverted) {
                inverted(pivotX = shapeWidth / 2F, pivotY = shapeHeight / 2F)
            }
        }.let(Outline::Generic)
    }
}

/**
 * Rectangle shape with concave/convex shaped sides and additional convex cutout at the bottom.
 * Useful for Single Vertical Bar and Stacked Bars Plot rendering.
 *
 * Primary constructor:
 * @param concaveConvexShape The internal shape logic used for rendering.
 * @param value The [VerticalBarPlotEntry] that defines the cutouts for the [ConcaveConvexShape].
 *
 * Secondary constructor:
 * @param xyGraphScope Provides access to [yAxisModel] and acts as an implementation of [XYGraphScope].
 * @param value The [VerticalBarPlotEntry] used to construct the internal shape as well as the additional convex cutout.
 */
@Stable
public class ConvexConcaveConvexShape<X, E : VerticalBarPlotEntry<X, Float>> private constructor(
    private val concaveConvexShape: ConcaveConvexShape<X, E>,
    private val index: Int,
    private val value: E
) : Shape, XYGraphScope<X, Float> by concaveConvexShape {

    public constructor(
        xyGraphScope: XYGraphScope<X, Float>,
        index: Int,
        value: E
    ) : this(
        ConcaveConvexShape(xyGraphScope, index, value),
        index,
        value
    )

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val shapeWidth = size.width
        val shapeHeight = size.height
        val arcRadius = shapeWidth / 2

        // Rendering negative values
        val isInverted = value.y.yMax < value.y.yMin
        // Required for proper bar rendering in waterfall charts
        if (index == 0) {
            val outline = BiConvexShape.createOutline(size, layoutDirection, density) as Outline.Generic

            outline.path.apply {
                // Rendering bar in negative direction
                if (isInverted) {
                    inverted(pivotX = shapeWidth / 2F, pivotY = shapeHeight / 2F)
                }
            }
            return outline
        }

        val (yZeroOffset, yMinOffset, yMaxOffset) = yAxisModel.yOffsets(value.y.yMin, value.y.yMax)

        // Prevent division by zero
        if (yMaxOffset == yMinOffset) return Path().let(Outline::Generic)

        val heightOffsetRatio = size.height / (yMaxOffset - yMinOffset)
        val offsetToHeight = { offset: Float -> offset * heightOffsetRatio }

        val yMaxZeroOffset = yMaxOffset - yZeroOffset
        val yMaxZeroHeight = offsetToHeight(yMaxZeroOffset)

        val outline = concaveConvexShape.createOutline(size, layoutDirection, density) as Outline.Generic

        val cutoutRect = Path().apply {
            addRect(
                rect = Rect(
                    offset = Offset(0F, yMaxZeroHeight - arcRadius),
                    size = Size(shapeWidth, shapeWidth)
                )
            )
        }
        val cutoutArc = Path().apply {
            addArc(
                oval = Rect(
                    offset = Offset(0F, yMaxZeroHeight - shapeWidth),
                    size = Size(shapeWidth, shapeWidth)
                ),
                startAngleDegrees = 0F,
                sweepAngleDegrees = 180F
            )
        }
        val cutout = (cutoutRect - cutoutArc).apply {
            // Rendering bar in negative direction
            if (isInverted) {
                inverted(pivotX = shapeWidth / 2F, pivotY = shapeHeight / 2F)
            }
        }
        return (outline.path - cutout).let(Outline::Generic)
    }
}

private fun Path.inverted(pivotX: Float, pivotY: Float): Path {
    Matrix().apply {
        resetToPivotedTransform(
            pivotX = pivotX,
            pivotY = pivotY,
            rotationZ = 180F
        )
    }.let(::transform)
    return this
}

private fun AxisModel<Float>.yOffsets(yMin: Float, yMax: Float): YOffsets {
    val yZeroOffset = computeOffset(0F).coerceIn(0F, 1F)
    val yMinOffset = computeOffset(yMin).coerceIn(0f, 1f)
    val yMaxOffset = computeOffset(yMax).coerceIn(0f, 1f)
    return YOffsets(
        yZeroOffset = yZeroOffset,
        yMinOffset = yMinOffset,
        yMaxOffset = yMaxOffset
    )
}

private data class YOffsets(
    val yZeroOffset: Float,
    val yMinOffset: Float,
    val yMaxOffset: Float
)
