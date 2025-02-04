package me.hufman.androidautoidrive.cds

import com.google.gson.JsonObject
import io.bimmergestalt.idriveconnectkit.CDS
import kotlinx.coroutines.flow.*
import me.hufman.androidautoidrive.CarInformation
import me.hufman.androidautoidrive.utils.GsonNullable.tryAsDouble
import me.hufman.androidautoidrive.utils.GsonNullable.tryAsInt
import me.hufman.androidautoidrive.utils.GsonNullable.tryAsJsonObject
import me.hufman.androidautoidrive.utils.GsonNullable.tryAsJsonPrimitive
import me.hufman.androidautoidrive.utils.GsonNullable.tryAsString
import kotlin.math.max

class CDSMetrics(val carInfo: CarInformation) {
	companion object {
		fun compassDirection(heading: Float?) = when (heading) {
			null -> "-"
			in 0.0 .. 22.5 -> "N"
			in 22.5 .. 67.5 -> "NE"
			in 67.5 .. 112.5 -> "E"
			in 112.5 .. 157.5 -> "SE"
			in 157.5 .. 202.5 -> "S"
			in 202.5 .. 247.5 -> "SW"
			in 247.5 .. 302.5 -> "W"
			in 302.5 .. 347.5 -> "NW"
			in 347.5 .. 360.0 -> "N"
			else -> "-"
		}

		fun compassArrow(heading: Float?) = when (heading) {
			null -> ""
			in 0.0 .. 22.5 -> "↑"
			in 22.5 .. 67.5 -> "↗"
			in 67.5 .. 112.5 -> "→"
			in 112.5 .. 157.5 -> "↘"
			in 157.5 .. 202.5 -> "↓"
			in 202.5 .. 247.5 -> "↙"
			in 247.5 .. 302.5 -> "←"
			in 302.5 .. 347.5 -> "↖"
			in 347.5 .. 360.0 -> "↑"
			else -> ""
		}

		/*
			status: 0 - closed or tilted
			status: 1 - partially open  (not tilted)
			status: 2 - fully open
			tilt: 1->12 -> tilted (tilted degree)
			open: 1-50 -> how far is open
		 */
		fun parseWindowState(state: JsonObject?): WindowState {
			val status = state?.tryAsJsonPrimitive("status")?.tryAsInt ?: 0
			val tiltPosition = state?.tryAsJsonPrimitive("tiltPosition")?.tryAsInt?: 0
			val position = state?.tryAsJsonPrimitive("position")?.tryAsInt ?:
				state?.tryAsJsonPrimitive("openPosition")?.tryAsInt ?: 0
			val state = when {
				status == 0 && tiltPosition == 0 -> WindowState.State.CLOSED
				tiltPosition > 0 && position == 0 -> WindowState.State.TILTED
				position > 0 -> WindowState.State.OPENED
				else -> WindowState.State.CLOSED
			}
			val fullPosition = if (status == 2) {
				100
			} else {
				position * 2
			}
			return WindowState(state, fullPosition)
		}
	}

	data class WindowState(val state: State, val position: Int) {
		enum class State {
			CLOSED,
			TILTED,
			OPENED
		}
		val isOpen: Boolean = state != State.CLOSED
	}

	// unit conversions
	val units: Flow<CDSVehicleUnits> = carInfo.cachedCdsData.flow[CDS.VEHICLE.UNITS].map {
		CDSVehicleUnits.fromCdsProperty(it)
	}
	val unitsAverageConsumption: Flow<CDSVehicleUnits.Consumption> = carInfo.cachedCdsData.flow[CDS.DRIVING.AVERAGECONSUMPTION].map {
		CDSVehicleUnits.Consumption.fromValue(it.tryAsJsonObject("averageConsumption")?.getAsJsonPrimitive("unit")?.tryAsInt)
	}
	val unitsAverageSpeed: Flow<CDSVehicleUnits.Speed> = carInfo.cachedCdsData.flow[CDS.DRIVING.AVERAGESPEED].map {
		CDSVehicleUnits.Speed.fromValue(it.tryAsJsonObject("averageSpeed")?.getAsJsonPrimitive("unit")?.tryAsInt)
	}

	/** data points */
	// level
	val evLevel = carInfo.cachedCdsData.flow[CDS.SENSORS.SOCBATTERYHYBRID].mapNotNull {
		it.tryAsJsonPrimitive("SOCBatteryHybrid")?.tryAsDouble?.takeIf { it < 255 }
	}
	val fuelLevel = carInfo.cachedCdsData.flow[CDS.SENSORS.FUEL].mapNotNull {
		it.tryAsJsonObject("fuel")?.tryAsJsonPrimitive("tanklevel")?.tryAsDouble?.takeIf { it > 0 }
	}.combine(units) { value, units ->
		units.fuelUnits.fromCarUnit(value)
	}
	val accBatteryLevel = carInfo.cachedCdsData.flow[CDS.SENSORS.BATTERY].mapNotNull {
		it.tryAsJsonPrimitive("battery")?.tryAsDouble?.takeIf { it < 255 }
	}

	// range
	val evRange = carInfo.cachedCdsData.flow[CDS.DRIVING.DISPLAYRANGEELECTRICVEHICLE].mapNotNull {
		it.tryAsJsonPrimitive("displayRangeElectricVehicle")?.tryAsDouble?.takeIf { it < 4093 }
	}
	val evRangeOrZero = flow { emit(0.0); emitAll(evRange) }
	val fuelRange = carInfo.cachedCdsData.flow[CDS.SENSORS.FUEL].mapNotNull {
		it.tryAsJsonObject("fuel")?.tryAsJsonPrimitive("range")?.tryAsDouble
	}.combine(units) { value, units ->
		units.distanceUnits.fromCarUnit(value)
	}.combine(evRangeOrZero) { totalRange, evRange ->
		max(0.0, totalRange - evRange)
	}
	val totalRange = carInfo.cachedCdsData.flow[CDS.SENSORS.FUEL].mapNotNull {
		it.tryAsJsonObject("fuel")?.tryAsJsonPrimitive("range")?.tryAsDouble
	}.combine(units) { value, units ->
		units.distanceUnits.fromCarUnit(value)
	}

	// temp
	val engineTemp = carInfo.cachedCdsData.flow[CDS.ENGINE.TEMPERATURE].mapNotNull {
		it.tryAsJsonObject("temperature")?.tryAsJsonPrimitive("engine")?.tryAsDouble?.takeIf { it < 255 }
	}.combine(units) { value, units ->
		units.temperatureUnits.fromCarUnit(value)
	}
	val oilTemp = carInfo.cachedCdsData.flow[CDS.ENGINE.TEMPERATURE].mapNotNull {
		it.tryAsJsonObject("temperature")?.tryAsJsonPrimitive("oil")?.tryAsDouble?.takeIf { it < 255 }
	}.combine(units) { value, units ->
		units.temperatureUnits.fromCarUnit(value)
	}
	val batteryTemp = carInfo.cdsData.flow[CDS.SENSORS.BATTERYTEMP].mapNotNull {
		it.tryAsJsonPrimitive("batteryTemp")?.tryAsDouble?.takeIf { it < 255 }
	}.combine(units) { value, units ->
		units.temperatureUnits.fromCarUnit(value)
	}

	val tempInterior = carInfo.cdsData.flow[CDS.SENSORS.TEMPERATUREINTERIOR].mapNotNull {
		it.tryAsJsonPrimitive("temperatureInterior")?.tryAsDouble
	}.combine(units) { value, units ->
		units.temperatureUnits.fromCarUnit(value)
	}
	val tempExterior = carInfo.cdsData.flow[CDS.SENSORS.TEMPERATUREEXTERIOR].mapNotNull {
		it.tryAsJsonPrimitive("temperatureExterior")?.tryAsDouble
	}.combine(units) { value, units ->
		units.temperatureUnits.fromCarUnit(value)
	}
	val tempExchanger = carInfo.cdsData.flow[CDS.CLIMATE.ACSYSTEMTEMPERATURES].mapNotNull {
		it.tryAsJsonObject("ACSystemTemperatures")?.tryAsJsonPrimitive("heatExchanger")?.tryAsDouble
	}
	val tempEvaporator = carInfo.cdsData.flow[CDS.CLIMATE.ACSYSTEMTEMPERATURES].mapNotNull {
		it.tryAsJsonObject("ACSystemTemperatures")?.tryAsJsonPrimitive("evaporator")?.tryAsDouble
	}

	/*
		0 - Initialisierung -> initialization
		1 - Tractionmodus -> Traction mode
		2 - Komfortmodus -> Comfort mode
		3 - Basismodus -> Basic mode
		4 - Sportmodus -> Sports mode
		5 - Sportmodusplus -> Sport+
		6 - Racemodus -> Race
		7 - Ecopro ->EcoPro
		8 - Ecoproplus -> EcoPro+
		9 - Komfortmoduserweitert -> Comfort mode extended
		xx - Adaptive Mode
		15 - Unknown

		Some cars have "+" modes (sport+, comfort+) and some other the same modes are called "Individual".
	 */
	val drivingMode = carInfo.cdsData.flow[CDS.DRIVING.MODE].mapNotNull {
		val a = it.tryAsJsonPrimitive("mode")?.tryAsInt
		when (a) {
			null -> ""
			2 -> "Comfort"
			9 -> "Comfort+"
			3 -> "Basic"
			4 -> "Sport"
			5 -> "Sport+"
			6 -> "Race"
			7 -> "EcoPro"
			8 -> "EcoPro+"
			else -> "-$a-"
		}
	}
	val drivingModeSport = drivingMode.map {
		it == "Sport" || it == "Sport+" || it == "Race"
	}
	val drivingGear = carInfo.cdsData.flow[CDS.DRIVING.GEAR].mapNotNull { it ->
		it.tryAsJsonPrimitive("gear")?.tryAsInt?.takeIf { it > 0 }
	}
	val drivingGearName = drivingGear.combine(drivingModeSport) { gear, isSporty ->
		when (gear) {
			1 -> "N"
			2 -> "R"
			3 -> "P"
			in 5..16 -> if (isSporty) {
				"S${gear - 4}"
			} else {
				"D${gear - 4}"
			}
			else -> "-"
		}
	}

	val speedActual = carInfo.cdsData.flow[CDS.DRIVING.SPEEDACTUAL].mapNotNull {
		it.tryAsJsonPrimitive("speedActual")?.tryAsDouble
	}.combine(units) { value, units ->
		units.distanceUnits.fromCarUnit(value)
	}
	val speedDisplayed = carInfo.cdsData.flow[CDS.DRIVING.SPEEDDISPLAYED].mapNotNull {
		it.tryAsJsonPrimitive("speedDisplayed")?.tryAsDouble
		// probably doesn't need unit conversion
	}.combine(units) { value, units ->
		units.distanceUnits.fromCarUnit(value)
	}
	val engineRpm = carInfo.cdsData.flow[CDS.ENGINE.RPMSPEED].mapNotNull {
		it.tryAsJsonPrimitive("RPMSpeed")?.tryAsInt
	}

	val heading = carInfo.cachedCdsData.flow[CDS.NAVIGATION.GPSEXTENDEDINFO].mapNotNull {
		var heading = it.tryAsJsonObject("GPSExtendedInfo")?.tryAsJsonPrimitive("heading")?.tryAsDouble?.toFloat()
		if (heading != null) {
			// heading defined in CCW manner, so we ned to invert to CW neutral direction wheel.
			heading *= -1
			heading += 360
			//heading = -100 + 360  = 260;
		}
		heading
	}
	val compassDirection = heading.map { heading ->
		compassDirection(heading)
	}
	val compassArrow = heading.map { heading ->
		compassArrow(heading)
	}

	var gearboxType = carInfo.cdsData.flow[CDS.ENGINE.INFO].mapNotNull {
		it.tryAsJsonObject("info")?.tryAsJsonPrimitive("gearboxType")?.tryAsInt
	}
	val accelerator = carInfo.cdsData.flow[CDS.DRIVING.ACCELERATORPEDAL].mapNotNull {
		it.tryAsJsonObject("acceleratorPedal")?.tryAsJsonPrimitive("position")?.tryAsInt
	}
	val acceleratorEco = carInfo.cdsData.flow[CDS.DRIVING.ACCELERATORPEDAL].mapNotNull {
		it.tryAsJsonObject("acceleratorPedal")?.tryAsJsonPrimitive("ecoPosition")?.tryAsInt
	}
	val brake = carInfo.cdsData.flow[CDS.DRIVING.BRAKECONTACT].map {
		it.tryAsJsonPrimitive("brakeContact")?.tryAsInt
	}
	val clutch = carInfo.cdsData.flow[CDS.DRIVING.CLUTCHPEDAL].map {
		it.tryAsJsonObject("clutchPedal")?.tryAsJsonPrimitive("position")?.tryAsInt
	}
	val steeringAngle = carInfo.cdsData.flow[CDS.DRIVING.STEERINGWHEEL].mapNotNull {
		it.tryAsJsonObject("steeringWheel")?.tryAsJsonPrimitive("angle")?.tryAsDouble
	}
	val parkingBrake = carInfo.cachedCdsData.flow[CDS.DRIVING.PARKINGBRAKE].mapNotNull {
		it.tryAsJsonPrimitive("parkingBrake")?.tryAsInt
	}
	val parkingBrakeSet = parkingBrake.map {
		it == 2 || it == 8 || it == 32
	}

	val accel = carInfo.cdsData.flow[CDS.DRIVING.ACCELERATION].mapNotNull {
		val accel = it.tryAsJsonObject("acceleration")
		val lat = accel?.tryAsJsonPrimitive("lateral")?.tryAsDouble?.takeIf { it < 65000 }
		val long = accel?.tryAsJsonPrimitive("longitudinal")?.tryAsDouble?.takeIf { it < 65000 }
		Pair(lat, long)
	}

	val sunroof = carInfo.cachedCdsData.flow[CDS.CONTROLS.SUNROOF].map {
		parseWindowState(it.tryAsJsonObject("sunroof"))
	}
	val windowDriverFront = carInfo.cachedCdsData.flow[CDS.CONTROLS.WINDOWDRIVERFRONT].map {
		parseWindowState(it.tryAsJsonObject("windowDriverFront"))
	}
	val windowPassengerFront = carInfo.cachedCdsData.flow[CDS.CONTROLS.WINDOWPASSENGERFRONT].map {
		parseWindowState(it.tryAsJsonObject("windowPassengerFront"))
	}
	val windowDriverRear = carInfo.cachedCdsData.flow[CDS.CONTROLS.WINDOWDRIVERREAR].map {
		parseWindowState(it.tryAsJsonObject("windowDriverRear"))
	}
	val windowPassengerRear = carInfo.cachedCdsData.flow[CDS.CONTROLS.WINDOWPASSENGERREAR].map {
		parseWindowState(it.tryAsJsonObject("windowPassengerRear"))
	}

	val gpsCountry = carInfo.cachedCdsData.flow[CDS.NAVIGATION.CURRENTPOSITIONDETAILEDINFO].mapNotNull {
		it.tryAsJsonObject("currentPositionDetailedInfo")?.tryAsJsonPrimitive("country")?.tryAsString
	}
	val gpsCity = carInfo.cachedCdsData.flow[CDS.NAVIGATION.CURRENTPOSITIONDETAILEDINFO].mapNotNull {
		it.tryAsJsonObject("currentPositionDetailedInfo")?.tryAsJsonPrimitive("city")?.tryAsString
	}
	val gpsStreet = carInfo.cachedCdsData.flow[CDS.NAVIGATION.CURRENTPOSITIONDETAILEDINFO].mapNotNull {
		it.tryAsJsonObject("currentPositionDetailedInfo")?.tryAsJsonPrimitive("street")?.tryAsString
	}
	val gpsCrossStreet = carInfo.cachedCdsData.flow[CDS.NAVIGATION.CURRENTPOSITIONDETAILEDINFO].mapNotNull {
		it.tryAsJsonObject("currentPositionDetailedInfo")?.tryAsJsonPrimitive("crossStreet")?.tryAsString
	}
	val gpsHouseNumber = carInfo.cachedCdsData.flow[CDS.NAVIGATION.CURRENTPOSITIONDETAILEDINFO].mapNotNull {
		it.tryAsJsonObject("currentPositionDetailedInfo")?.tryAsJsonPrimitive("houseNumber")?.tryAsString
	}

	val gpsAltitude = carInfo.cachedCdsData.flow[CDS.NAVIGATION.GPSEXTENDEDINFO].mapNotNull {
		it.tryAsJsonObject("GPSExtendedInfo")?.tryAsJsonPrimitive("altitude")?.tryAsInt?.takeIf { it < 32767 }
	}

	val gpsLat = carInfo.cachedCdsData.flow[CDS.NAVIGATION.GPSPOSITION].mapNotNull {
		it.tryAsJsonObject("GPSPosition")?.tryAsJsonPrimitive("latitude")?.tryAsDouble
	}
	val gpsLon = carInfo.cachedCdsData.flow[CDS.NAVIGATION.GPSPOSITION].mapNotNull {
		it.tryAsJsonObject("GPSPosition")?.tryAsJsonPrimitive("longitude")?.tryAsDouble
	}
}