package com.homemonitor.parent.ui.screens.location

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.homemonitor.parent.ui.theme.StatusSuccess
import com.homemonitor.parent.viewmodel.DeviceViewModel

object MapLibreOsmConfig {
    const val TILE_URL = "https://tile.openstreetmap.org/{z}/{x}/{y}.png"
    const val ATTRIBUTION = "© OpenStreetMap contributors"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapLocationScreen(deviceViewModel: DeviceViewModel) {
    val deviceState by deviceViewModel.uiState.collectAsState()
    val loc = deviceState.currentLocation

    val lat = loc?.optDouble("latitude", 37.7749) ?: 37.7749
    val lng = loc?.optDouble("longitude", -122.4194) ?: -122.4194

    var selectedChild by remember { mutableStateOf("Alex") }

    Box(modifier = Modifier.fillMaxSize()) {
        // Full Map Canvas Container (Life360 Style)
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = "Device Location Pin",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "MapLibre Vector Canvas (OSM Tiles)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Lat: %.4f • Lng: %.4f • Accuracy: 12m".format(lat, lng),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Top Filter Bar Overlay
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selectedChild == "All",
                        onClick = { selectedChild = "All" },
                        label = { Text("All Children") }
                    )
                    FilterChip(
                        selected = selectedChild == "Alex",
                        onClick = { selectedChild = "Alex" },
                        label = { Text("Alex") }
                    )
                    FilterChip(
                        selected = selectedChild == "Emma",
                        onClick = { selectedChild = "Emma" },
                        label = { Text("Emma") }
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = StatusSuccess.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = "Live • 2m ago",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = StatusSuccess,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // Bottom Draggable Location Detail Sheet
        Surface(
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                        .align(Alignment.CenterHorizontally)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "$selectedChild's Current Location",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "Central Park West, New York, NY",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.Navigation, contentDescription = "Directions", tint = MaterialTheme.colorScheme.primary)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {},
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("History", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    }

                    Button(
                        onClick = {},
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Icon(Icons.Default.AddLocation, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add Geofence", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "ACTIVE GEOFENCES (SAFE ZONES)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                GeofenceItem(name = "Home (Radius: 200m)", status = "Inside Safe Zone ✅")
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                GeofenceItem(name = "School (Radius: 500m)", status = "Outside Zone")
            }
        }
    }
}

@Composable
private fun GeofenceItem(name: String, status: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(name, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
        Text(status, fontSize = 12.sp, color = StatusSuccess)
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
fun MapLocationPreview() {
    com.homemonitor.parent.ui.theme.HomeMonitorTheme {
        MapLocationScreen(
            deviceViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
        )
    }
}
