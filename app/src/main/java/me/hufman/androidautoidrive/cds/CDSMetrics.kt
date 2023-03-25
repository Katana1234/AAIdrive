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
import kotlin.math.absoluteValue
import kotlin.math.max

class CDSMetrics(val carInfo: CarInformation) {

	// unit conversions
	val units: Flow<CDSVehicleUnits> = carInfo.cachedCdsData.flow[CDS.VEHICLE.UNITS].map {
		CDSVehicleUnits.fromCdsProperty(it)
	}
	val unitsAverageConsumption: Flow<CDSVehicleUnits.Consumption> =
		carInfo.cachedCdsData.flow[CDS.DRIVING.AVERAGECONSUMPTION].map {
			CDSVehicleUnits.Consumption.fromValue(
				it.tryAsJsonObject("averageConsumption")?.getAsJsonPrimitive("unit")?.tryAsInt
			)
		}
	val unitsAverageSpeed: Flow<CDSVehicleUnits.Speed> =
		carInfo.cachedCdsData.flow[CDS.DRIVING.AVERAGESPEED].map {
			CDSVehicleUnits.Speed.fromValue(
				it.tryAsJsonObject("averageSpeed")?.getAsJsonPrimitive("unit")?.tryAsInt
			)
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
		it.tryAsJsonObject("temperature")
			?.tryAsJsonPrimitive("engine")?.tryAsDouble?.takeIf { it < 255 }
	}.combine(units) { value, units ->
		units.temperatureUnits.fromCarUnit(value)
	}
	val oilTemp = carInfo.cachedCdsData.flow[CDS.ENGINE.TEMPERATURE].mapNotNull {
		it.tryAsJsonObject("temperature")
			?.tryAsJsonPrimitive("oil")?.tryAsDouble?.takeIf { it < 255 }
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
	/*
	val tempEvaporator = carInfo.cdsData.flow[CDS.CLIMATE.ACSYSTEMTEMPERATURES].mapNotNull {
		it.tryAsJsonObject("ACSystemTemperatures")?.tryAsJsonPrimitive("evaporator")?.tryAsDouble
	}
	*/

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
	val drivingGear = carInfo.cdsData.flow[CDS.DRIVING.GEAR].mapNotNull {
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


	val accelerator = carInfo.cdsData.flow[CDS.DRIVING.ACCELERATORPEDAL].mapNotNull {
		it.tryAsJsonObject("acceleratorPedal")?.tryAsJsonPrimitive("position")?.tryAsInt
	}
	/*
	val acceleratorEco = carInfo.cdsData.flow[CDS.DRIVING.ACCELERATORPEDAL].mapNotNull {
		it.tryAsJsonObject("acceleratorPedal")?.tryAsJsonPrimitive("ecoPosition")?.tryAsDouble
	}
	*/
	/*
	val brake = carInfo.cdsData.flow[CDS.DRIVING.BRAKECONTACT].mapNotNull {
		it.tryAsJsonPrimitive("brakeContact")?.tryAsInt
	}
	*/
	/*
	val clutch = carInfo.cdsData.flow[CDS.DRIVING.CLUTCHPEDAL].mapNotNull {
		it.tryAsJsonObject("clutchPedal")?.tryAsJsonPrimitive("position")?.tryAsInt
	}
	val steeringAngle = carInfo.cdsData.flow[CDS.DRIVING.STEERINGWHEEL].mapNotNull {
		it.tryAsJsonObject("steeringWheel")?.tryAsJsonPrimitive("angle")?.tryAsDouble
	}
	*/


	val brakeContact = carInfo.cdsData.flow[CDS.DRIVING.BRAKECONTACT].map {
		it.tryAsJsonPrimitive("brakeContact")?.tryAsInt
	}
	val parkingBrake = carInfo.cachedCdsData.flow[CDS.DRIVING.PARKINGBRAKE].map {
		it.tryAsJsonPrimitive("parkingBrake")?.tryAsInt
	}
	val parkingBrakeSet = parkingBrake.map {
		it == 2 || it == 8 || it == 32
	}
	val brakeInfo = brakeContact.combine(parkingBrakeSet) { brakeContact, parkingBrakeSet ->
		// brakeContact is a bit coded value
		// bit 1 -> value 1 -> Brake pedal?
		// bit 2 -> value 2 -> Soft braking
		// bit 3 -> value 3 -> Medium / Strong braking
		// bit 4 -> value 8 -> Cruise control is braking
		// bit 5 -> value 16 -> Fullstop (when the vehicle is standing still, only when engine is running)

		// Choosing the "most interesting" bit in the right order because multiple bits might be set
		var brakeString = "-"
		if (brakeContact?.and(8) ?: 0 == 8) {
			brakeString = "Cruise Control"
		} else if (brakeContact?.and(4) ?: 0 == 4) {
			brakeString = "Strong"
		} else if (brakeContact?.and(2) ?: 0 == 2) {
			brakeString = "Soft"
		}
		// Adding the parking brake info
		if (parkingBrakeSet) {
			if (brakeString == "-") {
				brakeString = "( ! )"
			} else {
				brakeString += " ( ! )"
			}
		}
		brakeString
	}

	var clutchPedalPosition = carInfo.cdsData.flow[CDS.DRIVING.CLUTCHPEDAL].mapNotNull {
		it.tryAsJsonObject("clutchPedal")?.tryAsJsonPrimitive("position")?.tryAsInt
	}
	var gearboxType = carInfo.cdsData.flow[CDS.ENGINE.INFO].mapNotNull {
		it.tryAsJsonObject("info")?.tryAsJsonPrimitive("gearboxType")?.tryAsInt
	}

	val clutchInfo =
		clutchPedalPosition.combine(gearboxType) { clutchPedalPosition, gearboxType ->
			if (gearboxType == 1) {
				//automatic transmission with torque converter
				when (clutchPedalPosition) {
					null -> ""
					0 -> "Coupled"
					1 -> "Sailing"
					2 -> "Uncoupled"
					3 -> "Open"
					else -> "-$clutchPedalPosition-"
				}
			} else {
				// unknown transmission type -> i don't know the values of clutchPedalPosition and gearboxType
				// for manual transmissions, dual clutch transmissions and electric vehicles
				"-$clutchPedalPosition-"
			}
		}

	val steeringWheel = carInfo.cdsData.flow[CDS.DRIVING.STEERINGWHEEL].mapNotNull {
		val a = it.tryAsJsonObject("steeringWheel")?.tryAsJsonPrimitive("angle")?.tryAsDouble
		var dir = when {
			a == null -> ""
			a < 0 -> "R"
			a > 0 -> "L"
			else -> ""
		}
		val b = String.format("%.1f", a?.absoluteValue)
		"$b°$dir"
	}


	val gpsCountry =
		carInfo.cachedCdsData.flow[CDS.NAVIGATION.CURRENTPOSITIONDETAILEDINFO].mapNotNull {
			it.tryAsJsonObject("currentPositionDetailedInfo")
				?.tryAsJsonPrimitive("country")?.tryAsString?.takeIf { it.isNotEmpty() }
		}
	val gpsCity =
		carInfo.cachedCdsData.flow[CDS.NAVIGATION.CURRENTPOSITIONDETAILEDINFO].mapNotNull {
			it.tryAsJsonObject("currentPositionDetailedInfo")
				?.tryAsJsonPrimitive("city")?.tryAsString?.takeIf { it.isNotEmpty() }
		}
	val gpsStreet =
		carInfo.cachedCdsData.flow[CDS.NAVIGATION.CURRENTPOSITIONDETAILEDINFO].mapNotNull {
			it.tryAsJsonObject("currentPositionDetailedInfo")
				?.tryAsJsonPrimitive("street")?.tryAsString?.takeIf { it.isNotEmpty() }
		}
	val gpsCrossStreet =
		carInfo.cachedCdsData.flow[CDS.NAVIGATION.CURRENTPOSITIONDETAILEDINFO].mapNotNull {
			it.tryAsJsonObject("currentPositionDetailedInfo")
				?.tryAsJsonPrimitive("crossStreet")?.tryAsString?.takeIf { it.isNotEmpty() }
		}
	val gpsHouseNumber =
		carInfo.cachedCdsData.flow[CDS.NAVIGATION.CURRENTPOSITIONDETAILEDINFO].mapNotNull {
			it.tryAsJsonObject("currentPositionDetailedInfo")
				?.tryAsJsonPrimitive("houseNumber")?.tryAsString?.takeIf { it.isNotEmpty() }
		}

	val gpsAltitude =
		carInfo.cachedCdsData.flow[CDS.NAVIGATION.GPSEXTENDEDINFO].mapNotNull {
			it.tryAsJsonObject("GPSExtendedInfo")
				?.tryAsJsonPrimitive("altitude")?.tryAsInt?.takeIf { it < 32767 }
		}

	val gpsLat =
		carInfo.cachedCdsData.flow[CDS.NAVIGATION.GPSPOSITION].mapNotNull {
			it.tryAsJsonObject("GPSPosition")
				?.tryAsJsonPrimitive("latitude")?.tryAsDouble
		}
	val gpsLon =
		carInfo.cachedCdsData.flow[CDS.NAVIGATION.GPSPOSITION].mapNotNull {
			it.tryAsJsonObject("GPSPosition")
				?.tryAsJsonPrimitive("longitude")?.tryAsDouble
		}

	val gpsHeading = carInfo.cachedCdsData.flow[CDS.NAVIGATION.GPSEXTENDEDINFO].map {
		var heading = it.tryAsJsonObject("GPSExtendedInfo")?.tryAsJsonPrimitive("heading")?.tryAsInt
		if (heading != null) {
			heading *= -1
			heading += 360
		}
		heading
	}
	val gpsDirection = carInfo.cachedCdsData.flow[CDS.NAVIGATION.GPSEXTENDEDINFO].map {
		var heading =
			it.tryAsJsonObject("GPSExtendedInfo")?.tryAsJsonPrimitive("heading")?.tryAsDouble
		if (heading != null) {
			// heading defined in CCW manner, so we ned to invert to CW neutral direction wheel.
			heading *= -1
			heading += 360
			//heading = -100 + 360  = 260;

			if ((heading >= 0 && heading < 22.5) || (heading >= 347.5 && heading <= 360)) {
				"N"
			} else if (heading >= 22.5 && heading < 67.5) {
				"NE"
			} else if (heading >= 67.5 && heading < 112.5) {
				"E"
			} else if (heading >= 112.5 && heading < 157.5) {
				"SE"
			} else if (heading >= 157.5 && heading < 202.5) {
				"S"
			} else if (heading >= 202.5 && heading < 247.5) {
				"SW"
			} else if (heading >= 247.5 && heading < 302.5) {
				"W"
			} else if (heading >= 302.5 && heading < 347.5) {
				"NW"
			} else {
				"-"
			}
		} else {
			""
		}
	}

	val accLon =
		carInfo.cachedCdsData.flow[CDS.DRIVING.ACCELERATION].mapNotNull {
			it.tryAsJsonObject("acceleration")
				?.tryAsJsonPrimitive("longitudinal")?.tryAsDouble?.div(9.80665)// ms/s² to G
		}

	val accLat =
		carInfo.cachedCdsData.flow[CDS.DRIVING.ACCELERATION].mapNotNull {
			it.tryAsJsonObject("acceleration")
				?.tryAsJsonPrimitive("lateral")?.tryAsDouble?.div(9.80665) // ms/s² to G
		}

	val engineRpm =
		carInfo.cachedCdsData.flow[CDS.ENGINE.RPMSPEED].mapNotNull {
			it.tryAsJsonPrimitive("RPMSpeed")?.tryAsInt
		}

	val sunRoof = carInfo.cachedCdsData.flow[CDS.CONTROLS.SUNROOF].map {
		val status = it.tryAsJsonObject("sunroof")?.tryAsJsonPrimitive("status")?.tryAsInt ?: 0
		val openPosition =
			it.tryAsJsonObject("sunroof")?.tryAsJsonPrimitive("openPosition")?.tryAsInt ?: 0
		val tiltPosition =
			it.tryAsJsonObject("sunroof")?.tryAsJsonPrimitive("tiltPosition")?.tryAsInt ?: 0
		if (status == 0 && tiltPosition == 0 && openPosition == 0) {
			"Closed"
		} else if (tiltPosition > 0 && openPosition == 0) {
			"Tilted"
		} else if (status == 1 && openPosition > 0) {
			"Opened, ${openPosition * 2}%"
		} else if (status == 2) {
			"Fully opened"
		} else {
			""
		}
	}

	private fun parseWindowStatus(data: JsonObject?): String {
		val status = data?.tryAsJsonPrimitive("status")?.tryAsInt ?: 0
		val position = data?.tryAsJsonPrimitive("position")?.tryAsInt ?: 0
		return if (status == 0) {
			"Closed"
		} else if (status == 1) {
			"Opened, ${position * 2}%"
		} else {
			"Fully opened"
		}
	}

	val windowDriverFrontState = carInfo.cachedCdsData.flow[CDS.CONTROLS.WINDOWDRIVERFRONT].map {
		parseWindowStatus(it.tryAsJsonObject("windowDriverFront"))
	}
	val windowPassengerFrontState =
		carInfo.cachedCdsData.flow[CDS.CONTROLS.WINDOWPASSENGERFRONT].map {
			parseWindowStatus(it.tryAsJsonObject("windowPassengerFront"))
		}
	val windowDriverRearState = carInfo.cachedCdsData.flow[CDS.CONTROLS.WINDOWDRIVERREAR].map {
		parseWindowStatus(it.tryAsJsonObject("windowDriverRear"))
	}
	val windowPassengerRearState =
		carInfo.cachedCdsData.flow[CDS.CONTROLS.WINDOWPASSENGERREAR].map {
			parseWindowStatus(it.tryAsJsonObject("windowPassengerRear"))
		}


}