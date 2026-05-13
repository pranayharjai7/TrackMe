package com.trackme.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.trackme.ui.theme.Teal

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun <T> WheelPicker(
    items: List<T>,
    initialIndex: Int = 0,
    itemHeight: Dp = 32.dp,
    visibleItemsCount: Int = 3,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.titleMedium,
    selectedColor: Color = Teal,
    unselectedColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
    onItemSelected: (T) -> Unit,
    itemToString: (T) -> String = { it.toString() }
) {
    require(visibleItemsCount % 2 != 0) { "visibleItemsCount must be odd" }
    
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = maxOf(0, initialIndex))
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
    val haptic = LocalHapticFeedback.current
    
    val centerItemIndex = remember { derivedStateOf { 
        val layoutInfo = listState.layoutInfo
        val visibleItems = layoutInfo.visibleItemsInfo
        if (visibleItems.isEmpty()) return@derivedStateOf -1
        
        val center = layoutInfo.viewportStartOffset + (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset) / 2
        
        var closestItemIndex = -1
        var minDistance = Int.MAX_VALUE
        
        for (itemInfo in visibleItems) {
            val itemCenter = itemInfo.offset + itemInfo.size / 2
            val distance = kotlin.math.abs(itemCenter - center)
            if (distance < minDistance) {
                minDistance = distance
                closestItemIndex = itemInfo.index
            }
        }
        closestItemIndex
    } }
    
    LaunchedEffect(centerItemIndex.value) {
        if (centerItemIndex.value in items.indices) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onItemSelected(items[centerItemIndex.value])
        }
    }
    
    val itemHeightPx = with(LocalDensity.current) { itemHeight.toPx() }
    val halfVisibleCount = visibleItemsCount / 2
    
    LazyColumn(
        state = listState,
        flingBehavior = flingBehavior,
        modifier = modifier
            .height(itemHeight * visibleItemsCount)
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(vertical = itemHeight * halfVisibleCount)
    ) {
        items(items.size, key = { it }) { index ->
            val isCenter by remember { derivedStateOf { centerItemIndex.value == index } }
            
            // Calculate distance from center for scaling and alpha
            val distance by remember { derivedStateOf {
                val layoutInfo = listState.layoutInfo
                val itemInfo = layoutInfo.visibleItemsInfo.find { it.index == index }
                if (itemInfo == null) return@derivedStateOf halfVisibleCount + 1f
                
                val center = layoutInfo.viewportStartOffset + (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset) / 2f
                val itemCenter = itemInfo.offset + itemInfo.size / 2f
                kotlin.math.abs(itemCenter - center) / itemHeightPx
            } }
            
            val scale by animateFloatAsState(
                targetValue = if (distance < 1f) 1f - (distance * 0.15f) else 0.85f,
                animationSpec = tween(150), label = "scale"
            )
            
            val alpha by animateFloatAsState(
                targetValue = if (distance < 1f) 1f else maxOf(0.2f, 1f - (distance * 0.4f)),
                animationSpec = tween(150), label = "alpha"
            )
            
            Box(
                modifier = Modifier
                    .height(itemHeight)
                    .fillMaxWidth()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .alpha(alpha),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = itemToString(items[index]),
                    style = textStyle,
                    color = if (isCenter) selectedColor else unselectedColor,
                    fontWeight = if (isCenter) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun <T> HorizontalWheelPicker(
    items: List<T>,
    initialIndex: Int = 0,
    itemWidth: Dp = 64.dp,
    visibleItemsCount: Int = 3,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.titleMedium,
    selectedColor: Color = Teal,
    unselectedColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
    onItemSelected: (T) -> Unit,
    itemToString: (T) -> String = { it.toString() }
) {
    require(visibleItemsCount % 2 != 0) { "visibleItemsCount must be odd" }
    
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = maxOf(0, initialIndex))
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
    val haptic = LocalHapticFeedback.current
    
    val centerItemIndex = remember { derivedStateOf { 
        val layoutInfo = listState.layoutInfo
        val visibleItems = layoutInfo.visibleItemsInfo
        if (visibleItems.isEmpty()) return@derivedStateOf -1
        
        val center = layoutInfo.viewportStartOffset + (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset) / 2
        
        var closestItemIndex = -1
        var minDistance = Int.MAX_VALUE
        
        for (itemInfo in visibleItems) {
            val itemCenter = itemInfo.offset + itemInfo.size / 2
            val distance = kotlin.math.abs(itemCenter - center)
            if (distance < minDistance) {
                minDistance = distance
                closestItemIndex = itemInfo.index
            }
        }
        closestItemIndex
    } }
    
    LaunchedEffect(centerItemIndex.value) {
        if (centerItemIndex.value in items.indices) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onItemSelected(items[centerItemIndex.value])
        }
    }
    
    val itemWidthPx = with(LocalDensity.current) { itemWidth.toPx() }
    val halfVisibleCount = visibleItemsCount / 2
    
    androidx.compose.foundation.lazy.LazyRow(
        state = listState,
        flingBehavior = flingBehavior,
        modifier = modifier
            .width(itemWidth * visibleItemsCount),
        verticalAlignment = Alignment.CenterVertically,
        contentPadding = PaddingValues(horizontal = itemWidth * halfVisibleCount)
    ) {
        items(items.size, key = { it }) { index ->
            val isCenter by remember { derivedStateOf { centerItemIndex.value == index } }
            
            // Calculate distance from center for scaling and alpha
            val distance by remember { derivedStateOf {
                val layoutInfo = listState.layoutInfo
                val itemInfo = layoutInfo.visibleItemsInfo.find { it.index == index }
                if (itemInfo == null) return@derivedStateOf halfVisibleCount + 1f
                
                val center = layoutInfo.viewportStartOffset + (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset) / 2f
                val itemCenter = itemInfo.offset + itemInfo.size / 2f
                kotlin.math.abs(itemCenter - center) / itemWidthPx
            } }
            
            val scale by animateFloatAsState(
                targetValue = if (distance < 1f) 1f - (distance * 0.15f) else 0.85f,
                animationSpec = tween(150), label = "scale"
            )
            
            val alpha by animateFloatAsState(
                targetValue = if (distance < 1f) 1f else maxOf(0.2f, 1f - (distance * 0.4f)),
                animationSpec = tween(150), label = "alpha"
            )
            
            Box(
                modifier = Modifier
                    .width(itemWidth)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .alpha(alpha),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = itemToString(items[index]),
                    style = textStyle,
                    color = if (isCenter) selectedColor else unselectedColor,
                    fontWeight = if (isCenter) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}
