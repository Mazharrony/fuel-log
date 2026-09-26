package com.fuelexpenselog.app.ui.vehicle

import com.fuelexpenselog.app.data.prefs.AppPrefs
import com.fuelexpenselog.app.data.repo.FuelLogRepository
import com.fuelexpenselog.domain.consumption.ConsumptionResult
import com.fuelexpenselog.domain.model.HistoryEntry
import com.fuelexpenselog.domain.model.Vehicle
import com.fuelexpenselog.domain.unit.ConsumptionFormat
import com.fuelexpenselog.domain.unit.EnergyKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn

/**
 * Everything the vehicle screens derive their figures from, recomputed on every change to
 * any of it. Nothing here is cached or stored: a new entry anywhere re-runs the engine, and
 * every screen agrees because they all read this.
 */
data class VehicleSnapshot(
    val vehicle: Vehicle,
    /** Newest first. */
    val history: List<HistoryEntry>,
    val consumption: ConsumptionResult,
    /** Already resolved: the vehicle's own format, else the app's. */
    val format: ConsumptionFormat,
    val odometerM: Long?,
)

/** Null once the vehicle is gone - deleted from another screen, or by a restore. */
@OptIn(ExperimentalCoroutinesApi::class)
fun FuelLogRepository.observeSnapshot(vehicleId: Long, prefs: AppPrefs): Flow<VehicleSnapshot?> =
    observeVehicle(vehicleId).flatMapLatest { vehicle ->
        if (vehicle == null) {
            flowOf(null)
        } else {
            combine(
                observeHistory(vehicleId),
                observeConsumption(vehicle),
                prefs.observeConsumptionFormat(),
                observeCurrentOdometer(vehicleId),
            ) { history, consumption, appFormat, odometer ->
                VehicleSnapshot(
                    vehicle = vehicle,
                    history = history,
                    consumption = consumption,
                    // The only route to a format: a kind mismatch would make the domain throw.
                    format = vehicle.formatFor(EnergyKind.LIQUID, appFormat),
                    odometerM = odometer,
                )
            }
        }
    }.flowOn(Dispatchers.Default)
