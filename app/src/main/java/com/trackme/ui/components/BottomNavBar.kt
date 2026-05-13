package com.trackme.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.trackme.ui.navigation.Routes
import com.trackme.ui.theme.*

private data class BottomNavItem(
    val label: String,
    val icon: ImageVector,
    val route: String,
)

@Composable
fun TrackMeBottomBar(navController: NavController, currentRoute: String?) {
    val items = listOf(
        BottomNavItem("Home", Icons.Filled.Home, Routes.Home.route),
        BottomNavItem("Workout", Icons.Filled.FitnessCenter, Routes.WeeklyPlanner.route),
        BottomNavItem("Progress", Icons.Filled.BarChart, Routes.Progress.route),
        BottomNavItem("Profile", Icons.Filled.Person, Routes.Profile.route),
    )
    
    val selectedIndex = items.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)

    // Full-bottom unified navigation container
    Surface(
        modifier = Modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        color = SurfaceVariant, // Solid opaque block
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
        shadowElevation = 16.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding() // Padding for system nav bar INSIDE the solid block
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
            ) {
                // Liquid Selection Bead (Sliding Background)
                val widthPerItem = 1f / items.size
                val targetXOffset = (selectedIndex.toFloat() + 0.5f) * widthPerItem
                
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val indicatorWidth = 48.dp
                    val indicatorX = (maxWidth * targetXOffset) - (indicatorWidth / 2)
                    val animatedX by animateDpAsState(
                        targetValue = indicatorX,
                        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow),
                        label = "bead"
                    )

                    // The Glowing Bead
                    Box(
                        modifier = Modifier
                            .offset(x = animatedX, y = 84.dp)
                            .size(width = indicatorWidth, height = 4.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Violet.copy(alpha = 0.6f), Violet, Violet.copy(alpha = 0.6f))
                                )
                            )
                    )
                    
                    // Subtle Glow Aura behind the icon
                    Box(
                        modifier = Modifier
                            .offset(x = animatedX - 16.dp, y = 12.dp)
                            .size(80.dp)
                            .background(
                                Brush.radialGradient(
                                    listOf(Violet.copy(alpha = 0.15f), Color.Transparent)
                                )
                            )
                    )
                }

                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items.forEachIndexed { _, item ->
                        val isSelected = currentRoute == item.route
                        
                        CrystalNavItem(
                            item = item,
                            isSelected = isSelected,
                            onClick = {
                                if (currentRoute != item.route) {
                                    navController.navigate(item.route) {
                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CrystalNavItem(
    item: BottomNavItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(if (isSelected) 1.2f else 1.0f, label = "scale")
    val alpha by animateFloatAsState(if (isSelected) 1f else 0.5f, label = "alpha")

    Column(
        modifier = Modifier
            .width(80.dp)
            .fillMaxHeight()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = item.label,
            tint = if (isSelected) Color.White else Color.White.copy(alpha = 0.6f),
            modifier = Modifier
                .size(30.dp)
                .scale(scale)
                .alpha(alpha)
        )
        
        Spacer(Modifier.height(8.dp))
        
        Text(
            text = item.label,
            style = MaterialTheme.typography.labelMedium,
            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.6f),
            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
            fontSize = 12.sp,
            modifier = Modifier.alpha(alpha)
        )
    }
}
