package com.kaique.casahub.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.DeviceThermostat
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.Kitchen
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.MeetingRoom
import androidx.compose.material.icons.rounded.Power
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.ViewDay
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.ui.graphics.vector.ImageVector
import com.kaique.casahub.data.Device
import com.kaique.casahub.data.DeviceKind

fun Device.icon(): ImageVector = when (kind) {
    DeviceKind.LIGHT -> Icons.Rounded.Lightbulb
    DeviceKind.PLUG -> Icons.Rounded.Power
    DeviceKind.TV -> Icons.Rounded.Tv
    DeviceKind.SPEAKER -> Icons.Rounded.Speaker
    DeviceKind.THERMOSTAT -> Icons.Rounded.Thermostat
    DeviceKind.AC -> Icons.Rounded.AcUnit
    DeviceKind.LOCK -> if (state.locked == false) Icons.Rounded.LockOpen else Icons.Rounded.Lock
    DeviceKind.CONTACT -> Icons.Rounded.MeetingRoom
    DeviceKind.MOTION -> Icons.Rounded.DirectionsRun
    DeviceKind.LEAK -> Icons.Rounded.WaterDrop
    DeviceKind.SHADE -> Icons.Rounded.ViewDay
    DeviceKind.SENSOR -> Icons.Rounded.DeviceThermostat
    DeviceKind.APPLIANCE -> Icons.Rounded.Kitchen
    DeviceKind.CAMERA -> Icons.Rounded.Videocam
    DeviceKind.OTHER -> if (capabilities.isEmpty()) Icons.Rounded.Devices else Icons.Rounded.Sensors
}
