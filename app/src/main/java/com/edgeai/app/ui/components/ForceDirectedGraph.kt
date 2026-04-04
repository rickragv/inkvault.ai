package com.edgeai.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.sqrt
import kotlin.random.Random

data class GraphNode(
    val id: String,
    val label: String,
    val type: String,
    var x: Float = 0f,
    var y: Float = 0f,
)

data class GraphEdge(
    val sourceId: String,
    val targetId: String,
    val label: String = "",
)

@Composable
fun ForceDirectedGraph(
    nodes: List<GraphNode>,
    edges: List<GraphEdge>,
    iterations: Int = 100,
    modifier: Modifier = Modifier,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val layoutNodes = remember(nodes) {
        nodes.map { it.copy(x = Random.nextFloat() * 800, y = Random.nextFloat() * 800) }
    }

    // Run force-directed layout
    LaunchedEffect(layoutNodes, edges) {
        runForceLayout(layoutNodes, edges, iterations)
    }

    val nodeColor = MaterialTheme.colorScheme.primary
    val edgeColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
    val textColor = MaterialTheme.colorScheme.onSurface

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale *= zoom
                    offset += pan
                }
            },
    ) {
        val cx = size.width / 2 + offset.x
        val cy = size.height / 2 + offset.y

        // Draw edges
        for (edge in edges) {
            val source = layoutNodes.find { it.id == edge.sourceId } ?: continue
            val target = layoutNodes.find { it.id == edge.targetId } ?: continue
            drawLine(
                color = edgeColor,
                start = Offset(source.x * scale + cx, source.y * scale + cy),
                end = Offset(target.x * scale + cx, target.y * scale + cy),
                strokeWidth = 1.5f,
            )
        }

        // Draw nodes
        for (node in layoutNodes) {
            val nx = node.x * scale + cx
            val ny = node.y * scale + cy
            val color = entityTypeColor(node.type)

            drawCircle(
                color = color,
                radius = 16f * scale,
                center = Offset(nx, ny),
            )

            drawNodeLabel(node.label, nx, ny + 24f * scale, textColor)
        }
    }
}

private fun DrawScope.drawNodeLabel(text: String, x: Float, y: Float, color: Color) {
    drawContext.canvas.nativeCanvas.drawText(
        text.take(15),
        x,
        y,
        android.graphics.Paint().apply {
            this.color = android.graphics.Color.argb(
                (color.alpha * 255).toInt(),
                (color.red * 255).toInt(),
                (color.green * 255).toInt(),
                (color.blue * 255).toInt(),
            )
            textSize = 28f
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
        },
    )
}

/**
 * Fruchterman-Reingold force-directed layout.
 */
private fun runForceLayout(
    nodes: List<GraphNode>,
    edges: List<GraphEdge>,
    iterations: Int,
) {
    if (nodes.isEmpty()) return
    val area = 600f * 600f
    val k = sqrt(area / nodes.size)

    var temperature = 60f

    for (iter in 0 until iterations) {
        // Repulsive forces between all pairs
        val dx = FloatArray(nodes.size)
        val dy = FloatArray(nodes.size)

        for (i in nodes.indices) {
            for (j in i + 1 until nodes.size) {
                val diffX = nodes[i].x - nodes[j].x
                val diffY = nodes[i].y - nodes[j].y
                val dist = maxOf(sqrt(diffX * diffX + diffY * diffY), 0.01f)
                val force = k * k / dist

                val fx = (diffX / dist) * force
                val fy = (diffY / dist) * force

                dx[i] += fx
                dy[i] += fy
                dx[j] -= fx
                dy[j] -= fy
            }
        }

        // Attractive forces along edges
        for (edge in edges) {
            val si = nodes.indexOfFirst { it.id == edge.sourceId }
            val ti = nodes.indexOfFirst { it.id == edge.targetId }
            if (si < 0 || ti < 0) continue

            val diffX = nodes[si].x - nodes[ti].x
            val diffY = nodes[si].y - nodes[ti].y
            val dist = maxOf(sqrt(diffX * diffX + diffY * diffY), 0.01f)
            val force = dist * dist / k

            val fx = (diffX / dist) * force
            val fy = (diffY / dist) * force

            dx[si] -= fx
            dy[si] -= fy
            dx[ti] += fx
            dy[ti] += fy
        }

        // Apply forces with temperature limiting
        for (i in nodes.indices) {
            val disp = sqrt(dx[i] * dx[i] + dy[i] * dy[i])
            if (disp > 0) {
                val clamp = minOf(disp, temperature) / disp
                nodes[i].x += dx[i] * clamp
                nodes[i].y += dy[i] * clamp
            }
        }

        temperature *= 0.95f
    }

    // Center the graph
    val avgX = nodes.map { it.x }.average().toFloat()
    val avgY = nodes.map { it.y }.average().toFloat()
    for (node in nodes) {
        node.x -= avgX
        node.y -= avgY
    }
}
