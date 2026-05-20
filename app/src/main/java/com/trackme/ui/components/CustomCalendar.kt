package com.trackme.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.trackme.ui.theme.Coral
import com.trackme.ui.theme.Teal
import com.trackme.ui.theme.Violet
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun CustomCalendar(
    selectedDate: LocalDate,
    visibleMonth: YearMonth,
    completedDays: Set<LocalDate>,
    onDateSelected: (LocalDate) -> Unit,
    onMonthChange: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CalendarHeader(
            visibleMonth = visibleMonth,
            onMonthChange = onMonthChange
        )
        Spacer(modifier = Modifier.height(16.dp))
        DaysOfWeekRow()
        Spacer(modifier = Modifier.height(8.dp))
        CalendarGrid(
            visibleMonth = visibleMonth,
            selectedDate = selectedDate,
            completedDays = completedDays,
            onDateSelected = onDateSelected
        )
    }
}

@Composable
private fun CalendarHeader(
    visibleMonth: YearMonth,
    onMonthChange: (Long) -> Unit
) {
    val formatter = remember { DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { onMonthChange(-1) }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous Month", tint = Color.White)
        }
        Text(
            text = visibleMonth.format(formatter),
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
        IconButton(onClick = { onMonthChange(1) }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next Month", tint = Color.White)
        }
    }
}

@Composable
private fun DaysOfWeekRow() {
    val daysOfWeek = remember {
        val firstDayOfWeek = DayOfWeek.MONDAY
        val days = DayOfWeek.values()
        val startIndex = firstDayOfWeek.ordinal
        Array(7) { days[(startIndex + it) % 7] }
    }
    Row(modifier = Modifier.fillMaxWidth()) {
        for (day in daysOfWeek) {
            Text(
                text = day.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.5f),
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun CalendarGrid(
    visibleMonth: YearMonth,
    selectedDate: LocalDate,
    completedDays: Set<LocalDate>,
    onDateSelected: (LocalDate) -> Unit
) {
    val daysInMonth = visibleMonth.lengthOfMonth()
    val firstDayOfMonth = visibleMonth.atDay(1)
    val firstDayOfWeekOffset = (firstDayOfMonth.dayOfWeek.value - DayOfWeek.MONDAY.value + 7) % 7
    val totalDays = daysInMonth + firstDayOfWeekOffset
    val rows = Math.ceil(totalDays / 7.0).toInt()

    Column(modifier = Modifier.fillMaxWidth()) {
        for (row in 0 until rows) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (col in 0 until 7) {
                    val index = row * 7 + col
                    val dayOfMonth = index - firstDayOfWeekOffset + 1
                    if (dayOfMonth in 1..daysInMonth) {
                        val date = visibleMonth.atDay(dayOfMonth)
                        CalendarDayCell(
                            date = date,
                            isSelected = date == selectedDate,
                            isToday = date == LocalDate.now(),
                            isCompleted = completedDays.contains(date),
                            onDateSelected = onDateSelected,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarDayCell(
    date: LocalDate,
    isSelected: Boolean,
    isToday: Boolean,
    isCompleted: Boolean,
    onDateSelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val isFuture = date.isAfter(LocalDate.now())
    
    val bgColor = when {
        isSelected -> Color.White.copy(alpha = 0.2f)
        isCompleted -> Teal.copy(alpha = 0.3f)
        else -> Color.Transparent
    }
    
    val borderColor = when {
        isSelected -> Color.White
        isToday -> Coral.copy(alpha = 0.8f)
        else -> Color.Transparent
    }
    
    val textColor = when {
        isSelected -> Color.White
        isCompleted -> Teal
        isToday -> Coral
        isFuture -> Color.White.copy(alpha = 0.3f)
        else -> Color.White.copy(alpha = 0.8f)
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(4.dp)
            .clip(CircleShape)
            .background(bgColor)
            .border(if (isSelected || isToday) 2.dp else 0.dp, borderColor, CircleShape)
            .clickable(enabled = !isFuture) { onDateSelected(date) },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = date.dayOfMonth.toString(),
                color = textColor,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isSelected || isToday || isCompleted) FontWeight.Bold else FontWeight.Normal
            )
            if (isCompleted && !isSelected) {
                Spacer(modifier = Modifier.height(2.dp))
                Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(Teal))
            }
        }
    }
}
