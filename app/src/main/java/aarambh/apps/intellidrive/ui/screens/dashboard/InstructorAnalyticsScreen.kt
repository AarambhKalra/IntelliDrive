package aarambh.apps.intellidrive.ui.screens.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import aarambh.apps.intellidrive.data.model.User
import aarambh.apps.intellidrive.ui.viewmodel.AnalyticsUiState
import aarambh.apps.intellidrive.ui.viewmodel.AnalyticsViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstructorAnalyticsScreen(
    student: User,
    onBack: () -> Unit,
    viewModel: AnalyticsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(student.uid) {
        viewModel.loadAnalytics(student.uid)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("Analytics", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(student.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("←", style = MaterialTheme.typography.headlineMedium)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            when (val state = uiState) {
                is AnalyticsUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is AnalyticsUiState.Error -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("⚠️", fontSize = 48.sp)
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = state.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
                is AnalyticsUiState.Success -> {
                    val analytics = state.analytics
                    val hasData = analytics.sessions.isNotEmpty()
                    
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            // Summary Cards
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                SummaryCard("Sessions", if (hasData) analytics.sessionCount.toString() else "0", Modifier.weight(1f))
                                SummaryCard("Safety Score", if (hasData) analytics.latestSafetyScore.toString() else "--", Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                SummaryCard("Harsh Braking", if (hasData) analytics.totalHarshBraking.toString() else "0", Modifier.weight(1f))
                                SummaryCard("Overspeed", if (hasData) analytics.totalOverspeed.toString() else "0", Modifier.weight(1f))
                            }
                        }

                        item {
                            Spacer(Modifier.height(16.dp))
                            Text("Safety Score Trend", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    if (hasData) {
                                        val scores = analytics.sessions.reversed().map { it.safetyScore }
                                        PremiumLineChart(
                                            scores = scores,
                                            modifier = Modifier.fillMaxWidth().height(220.dp).padding(vertical = 16.dp)
                                        )
                                    } else {
                                        Text("No real data yet. Showing example curve:", color = Color.Gray, style = MaterialTheme.typography.labelMedium)
                                        Spacer(Modifier.height(8.dp))
                                        PremiumLineChart(
                                            scores = listOf(60, 75, 70, 85, 90, 88, 96),
                                            modifier = Modifier.fillMaxWidth().height(220.dp).padding(vertical = 16.dp)
                                        )
                                    }
                                }
                            }
                        }

                        item {
                            Spacer(Modifier.height(16.dp))
                            Text("Recent Events", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            if (!hasData || analytics.events.isEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                Text("No recent events detected.", color = Color.Gray)
                            }
                        }
                        
                        items(analytics.events.take(5)) { event ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .background(MaterialTheme.colorScheme.errorContainer, shape = CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("⚠️", fontSize = 20.sp)
                                    }
                                    Spacer(Modifier.width(16.dp))
                                    Column {
                                        Text(
                                            text = event.eventType.uppercase().replace("_", " "), 
                                            fontWeight = FontWeight.Bold, 
                                            color = MaterialTheme.colorScheme.error
                                        )
                                        val date = Date(event.timestamp)
                                        val format = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
                                        Text(
                                            text = "Time: ${format.format(date)} | Value: ${"%.1f".format(event.sensorValue)}", 
                                            style = MaterialTheme.typography.bodySmall, 
                                            color = Color.Gray
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                else -> {}
            }
        }
    }
}

@Composable
fun SummaryCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = value, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
fun PremiumLineChart(scores: List<Int>, modifier: Modifier = Modifier) {
    val lineColor = MaterialTheme.colorScheme.primary
    val gradientColors = listOf(lineColor.copy(alpha = 0.4f), Color.Transparent)
    
    Canvas(modifier = modifier) {
        if (scores.isEmpty()) return@Canvas
        
        val maxScore = 100f
        val minScore = 0f
        
        val width = size.width
        val height = size.height
        
        val xStep = if (scores.size > 1) width / (scores.size - 1) else width
        
        val strokePath = Path()
        val fillPath = Path()
        
        val points = scores.mapIndexed { index, score ->
            val x = index * xStep
            val y = height - ((score - minScore) / (maxScore - minScore) * height)
            Offset(x, y)
        }
        
        // Draw grid lines
        val gridColor = Color.LightGray.copy(alpha = 0.3f)
        for (i in 0..4) {
            val y = height * (i / 4f)
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(width, y),
                strokeWidth = 1.dp.toPx()
            )
        }
        
        if (points.isNotEmpty()) {
            strokePath.moveTo(points.first().x, points.first().y)
            fillPath.moveTo(points.first().x, height)
            fillPath.lineTo(points.first().x, points.first().y)
            
            for (i in 0 until points.size - 1) {
                val p1 = points[i]
                val p2 = points[i + 1]
                
                val controlX1 = p1.x + (p2.x - p1.x) / 2f
                val controlY1 = p1.y
                val controlX2 = p1.x + (p2.x - p1.x) / 2f
                val controlY2 = p2.y
                
                strokePath.cubicTo(controlX1, controlY1, controlX2, controlY2, p2.x, p2.y)
                fillPath.cubicTo(controlX1, controlY1, controlX2, controlY2, p2.x, p2.y)
            }
            
            fillPath.lineTo(points.last().x, height)
            fillPath.close()
            
            // Draw gradient fill
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = gradientColors,
                    startY = 0f,
                    endY = height
                )
            )
            
            // Draw smooth line
            drawPath(
                path = strokePath,
                color = lineColor,
                style = Stroke(
                    width = 4.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
            
            // Draw points
            points.forEach { point ->
                drawCircle(
                    color = Color.White,
                    radius = 6.dp.toPx(),
                    center = point
                )
                drawCircle(
                    color = lineColor,
                    radius = 4.dp.toPx(),
                    center = point
                )
            }
        }
    }
}
