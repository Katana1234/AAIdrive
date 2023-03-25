package me.hufman.androidautoidrive.carapp.carinfo

import android.icu.number.NumberFormatter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import me.hufman.androidautoidrive.carapp.L
import me.hufman.androidautoidrive.cds.CDSMetrics
import me.hufman.androidautoidrive.cds.CDSVehicleUnits
import me.hufman.androidautoidrive.phoneui.FlowUtils.addContextUnit
import me.hufman.androidautoidrive.phoneui.FlowUtils.addPlainUnit
import me.hufman.androidautoidrive.phoneui.FlowUtils.format

class CarDetailedInfo(cdsMetrics: CDSMetrics) {

	// unit display
	val unitsTemperatureLabel: Flow<String> = cdsMetrics.units.map {
		when (it.temperatureUnits) {
			CDSVehicleUnits.Temperature.CELCIUS -> L.CARINFO_UNIT_C
			CDSVehicleUnits.Temperature.FAHRENHEIT -> L.CARINFO_UNIT_F
		}
	}

	val unitsDistanceLabel: Flow<String> = cdsMetrics.units.map {
		when (it.distanceUnits) {
			CDSVehicleUnits.Distance.Kilometers -> L.CARINFO_UNIT_KM
			CDSVehicleUnits.Distance.Miles -> L.CARINFO_UNIT_MI
		}
	}

	val unitsFuelLabel: Flow<String> = cdsMetrics.units.map {
		when (it.fuelUnits) {
			CDSVehicleUnits.Fuel.Liters -> L.CARINFO_UNIT_LITER
			CDSVehicleUnits.Fuel.Gallons_UK -> L.CARINFO_UNIT_GALUK
			CDSVehicleUnits.Fuel.Gallons_US -> L.CARINFO_UNIT_GALUS
		}
	}

	// data points
	val evLevelLabel = cdsMetrics.evLevel.format("%.1f%%").map { "$it ${L.CARINFO_EV_BATTERY}" }

	val fuelLevelLabel = cdsMetrics.fuelLevel.format("%.1f").addPlainUnit(unitsFuelLabel)
		.map { "$it ${L.CARINFO_FUEL}" }

	val accBatteryLevelLabel =
		cdsMetrics.accBatteryLevel.format("%.0f%%").map { "$it ${L.CARINFO_ACC_BATTERY}" }

	val engineTemp = cdsMetrics.engineTemp.format("%.0f").addPlainUnit(unitsTemperatureLabel)
		.map { "$it ${L.CARINFO_ENGINE}" }
	val oilTemp = cdsMetrics.oilTemp.format("%.0f").addPlainUnit(unitsTemperatureLabel)
		.map { "$it ${L.CARINFO_OIL}" }
	val batteryTemp = cdsMetrics.batteryTemp.format("%.0f").addPlainUnit(unitsTemperatureLabel)
		.map { "$it ${L.CARINFO_BATTERY}" }

	val tempInterior = cdsMetrics.tempInterior.format("%.1f").addPlainUnit(unitsTemperatureLabel)
		.map { "$it ${L.CARINFO_INTERIOR}" }
	val tempExterior = cdsMetrics.tempExterior.format("%.1f").addPlainUnit(unitsTemperatureLabel)
		.map { "$it ${L.CARINFO_EXTERIOR}" }
	val tempExchanger = cdsMetrics.tempExchanger.format("%.1f").addPlainUnit(unitsTemperatureLabel)
		.map { "$it ${L.CARINFO_EXCHANGER}" }

	val drivingMode = cdsMetrics.drivingMode.map { "Mode: $it" }
	val drivingGearLabel = cdsMetrics.drivingGearName.map { "${L.CARINFO_GEAR}: $it" }

	val speedActualLabel = cdsMetrics.speedActual.map { "Speed act.: ${it.toInt()}km/h" }
	val rpmLabel = cdsMetrics.engineRpm.map { "RPM: $it" }

	// driving detail fields, as examples
	// real ones would need translated labels
	val accelContact = cdsMetrics.accelerator.map { "Accel: $it%" }

	//val accelEcoContact = cdsMetrics.acceleratorEco.format("%.1f%%").map { "AccelEco $it" }
	//val clutchContact = cdsMetrics.clutch.map { "Clutch $it" }
	//val brakeContact = cdsMetrics.brake.map { "Brake: $it" }
	//val steeringAngle = cdsMetrics.steeringAngle.format("%.1f°").map { "Steering $it" }


	val brakeInfoLabel = cdsMetrics.brakeInfo.map { "Brake: $it" }

	val clutchInfoLabel = cdsMetrics.clutchInfo.map { "Clutch: $it" }
	val steeringWheelLabel = cdsMetrics.steeringWheel.map { "Steering: $it" }

	val countryLabel = cdsMetrics.gpsCountry.map { "Country: $it" }
	val cityLabel = cdsMetrics.gpsCity.map { "City: $it" }
	val streetLabel = cdsMetrics.gpsStreet.map { "Street: $it" }
	val crossStreetLabel = cdsMetrics.gpsCrossStreet.map { "X-Street: $it" }
	val houseNumberLabel = cdsMetrics.gpsHouseNumber.map { "House number: $it" }
	val headingLabel = cdsMetrics.gpsHeading.map { "Heading: $it°" }
	val directionLabel = cdsMetrics.gpsDirection.map { "Direction: $it" }
	val altitudeLabel = cdsMetrics.gpsAltitude.map { "Altitude: ${it}m" }
	val latitudeLabel = cdsMetrics.gpsLat.map { "Latitude: $it" }
	val longitudeLabel = cdsMetrics.gpsLon.map { "Longitude: $it" }

	val accLonLabel = cdsMetrics.accLon.format("%+.1f").map { "Accel. Lon.: ${it}G" }
	val accLatLabel = cdsMetrics.accLat.format("%+.1f").map { "Accel. Lan.: ${it}G" }

	val windowDriverFrontStateLabel = cdsMetrics.windowDriverFrontState.map { "Driver front: $it" }
	val windowPassengerFrontStateLabel = cdsMetrics.windowPassengerFrontState.map { "Passenger front: $it" }
	val windowDriverRearStateLabel = cdsMetrics.windowDriverRearState.map { "Driver rear: $it" }
	val windowPassengerRearStateLabel = cdsMetrics.windowPassengerRearState.map { "Passenger rear: $it" }
	val sunroofLabel = cdsMetrics.sunRoof.map { "Sunroof: $it" }

	// categories
	private val overviewFields: List<Flow<String>> = listOf(
		engineTemp, tempExterior,
		oilTemp, tempInterior,
		batteryTemp, tempExchanger,
		fuelLevelLabel, evLevelLabel,
		accBatteryLevelLabel, emptyFlow()
	)

	private val drivingFields: List<Flow<String>> = listOf(
		speedActualLabel, drivingMode,
		rpmLabel, drivingGearLabel,
		accelContact, clutchInfoLabel,
		brakeInfoLabel, accLonLabel,
		steeringWheelLabel, accLatLabel
	)

	private val gpsFields: List<Flow<String>> = listOf(
		countryLabel, directionLabel,
		cityLabel, headingLabel,
		streetLabel, altitudeLabel,
		crossStreetLabel, latitudeLabel,
		houseNumberLabel, longitudeLabel
	)

	private val windowsFields: List<Flow<String>> = listOf(
		windowDriverFrontStateLabel, windowPassengerFrontStateLabel,
		windowDriverRearStateLabel, windowPassengerRearStateLabel,
		emptyFlow(), emptyFlow(),
		sunroofLabel, emptyFlow()
	)

	/*
	private val experimentalFields: List<Flow<String>> = listOf(
		consumptionLabel, emptyFlow()
	)
	*/


	val categories = LinkedHashMap<String, List<Flow<String>>>().apply {
		put(L.CARINFO_TITLE, overviewFields)
		put("Driving", drivingFields)
		put("GPS", gpsFields)
		put("Windows", windowsFields)
		//put("Experimental", experimentalFields)

		// add more pages like this:
//		put("Driving Details", drivingFields)
	}
	val category = MutableStateFlow(categories.keys.first())
	val categoryFields: Flow<List<Flow<String>>> = category.map { categories[it] ?: emptyList() }
}