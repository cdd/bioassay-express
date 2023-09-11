/*
	BioAssay Express (BAE)

	Copyright 2016-2023 Collaborative Drug Discovery, Inc.

	Licensed under the Apache License, Version 2.0 (the "License");
	you may not use this file except in compliance with the License.
	You may obtain a copy of the License at

		http://www.apache.org/licenses/LICENSE-2.0

	Unless required by applicable law or agreed to in writing, software
	distributed under the License is distributed on an "AS IS" BASIS,
	WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
	See the License for the specific language governing permissions and
	limitations under the License.
*/

package com.cdd.bae.data;

import com.cdd.bae.data.DataObject.*;
import com.cdd.testutil.*;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.util.*;

import org.junit.jupiter.api.*;

/*
	Test for com.cdd.bae.data.DataMeasure
*/

public class DataMeasureTest
{
	private static final String INVALID_ID = "aaaecbc30fd4e5f221ab5c5d";
	DataStore store;
	DataMeasure dataMeasure;

	@BeforeEach
	public void initialize()
	{
		FauxMongo mongo = FauxMongo.getInstance("/testData/db/compound");
		dataMeasure = new DataMeasure(mongo.getDataStore());
	}

	@Test
	public void testIsAnything() throws IOException
	{
		assertThat(dataMeasure.isAnything(), is(true));

		assertThat(dataMeasure.countMeasurements(), is(2));
		dataMeasure.deleteMeasurementsForAssay(1L);
		assertThat(dataMeasure.countMeasurements(), is(0));

		assertThat(dataMeasure.isAnything(), is(false));
	}

	@Test
	public void testGetMeasurement()
	{
		Measurement measurement = dataMeasure.getMeasurement(getValidID());
		assertThat(measurement.assayID, is(1L));

		assertThat(dataMeasure.getMeasurement(INVALID_ID), is(nullValue()));
	}

	@Test
	public void testGetMeasurements()
	{
		List<Measurement> measurements = toList(dataMeasure.getMeasurements(1L));
		assertThat(measurements, hasSize(2));

		measurements = toList(dataMeasure.getMeasurements(1L, new String[]{"activity"}));
		assertThat(measurements, hasSize(1));

		measurements = toList(dataMeasure.getMeasurements(11111L));
		assertThat(measurements, hasSize(0));
	}

	@Test
	public void testUpdateMeasurement() throws IOException
	{
		assertThat(dataMeasure.countMeasurements(), is(2));

		Measurement measurement = dataMeasure.getMeasurement(getValidID());
		dataMeasure.updateMeasurement(measurement);
		assertThat(dataMeasure.countMeasurements(), is(2));

		measurement.id = null;
		dataMeasure.updateMeasurement(measurement);
		assertThat(dataMeasure.countMeasurements(), is(3));
	}

	@Test
	public void testAppendMeasurements() throws IOException
	{
		assertThat(dataMeasure.countMeasurements(), is(2));

		String validID = getValidID();
		Measurement measurement = dataMeasure.getMeasurement(validID);
		assertThat(measurement.compoundID.length, is(7));

		Measurement[] result = dataMeasure.appendMeasurements(measurement);
		assertThat(dataMeasure.countMeasurements(), is(3));
		assertThat(result.length, is(1));
		assertThat(result[0].id, not(is(validID)));

		int size = 2 * DataMeasure.LIMIT + 100;
		measurement.assayID = 111L;
		measurement.compoundID = new long[size];
		measurement.value = new Double[size];
		measurement.relation = new String[size];
		result = dataMeasure.appendMeasurements(measurement);
		assertThat(dataMeasure.countMeasurements(), is(6));
		assertThat(result.length, is(3));

		List<Measurement> measurements = toList(dataMeasure.getMeasurements(111L));
		assertThat(measurements, hasSize(1));
		assertThat(measurements.get(0).compoundID.length, is(size));
	}

	@Test
	public void testDeleteMeasurement() throws IOException
	{
		assertThat(dataMeasure.countMeasurements(), is(2));
		dataMeasure.deleteMeasurement(getValidID());
		assertThat(dataMeasure.countMeasurements(), is(1));
	}

	@Test
	public void testDeleteMeasurementsForAssay() throws IOException
	{
		assertThat(dataMeasure.countMeasurements(), is(2));
		dataMeasure.deleteMeasurementsForAssay(1L);
		assertThat(dataMeasure.countMeasurements(), is(0));
	}

	@Test
	public void testCounts()
	{
		assertThat(dataMeasure.countMeasurements(), is(2));
		assertThat(dataMeasure.countUniqueAssays(), is(1));
		assertThat(dataMeasure.countCompounds(1L), is(7));
	}

	@Test
	public void testWatermark() throws IOException
	{
		assertEquals(10000000, dataMeasure.getWatermarkMeasure());
		assertEquals(10000000, dataMeasure.nextWatermarkMeasure());
		assertEquals(10000001, dataMeasure.getWatermarkMeasure());
	}

	// ------------ private methods ------------

	private String getValidID()
	{
		Measurement[] measurements = dataMeasure.getMeasurements(1L);
		return measurements[0].id;
	}

	private <T> List<T> toList(T[] array)
	{
		return Arrays.asList(array);
	}
}
